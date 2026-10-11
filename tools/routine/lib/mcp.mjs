// The routine tool as an MCP server over stdio, so any AI harness that speaks the Model Context Protocol
// (Claude Code, Claude Desktop, Codex, Gemini CLI, Cursor, VS Code, …) can make Liora routines with it.
// Messages are JSON-RPC 2.0, one per line; nothing but protocol goes to stdout.

import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { createInterface } from 'node:readline';
import { readBackup, ownExercises, profile } from './backup.mjs';
import { EQUIPMENT, MUSCLES, TRACKING, loadCatalog, repoFile } from './catalog.mjs';
import { checkRoutineFile, report } from './check.mjs';
import { exerciseDetails, exerciseLine } from './text.mjs';

// Tools, as this server uses them, are the same in every protocol version so far, so it speaks the client's.
const LATEST = '2025-06-18';

const INSTRUCTIONS = `Makes workout routines for Liora, a workout tracker, as files the person imports on their phone.
1. Read liora_routine_format once: it's the file format, with examples.
2. Ask what you need and don't know: goal, days a week, time per session, experience, equipment, injuries, kg or lb.
   If they have a Liora backup, read it with liora_read_backup: their units, gyms, own exercises, routines and
   recent numbers, so weights fit them. Without numbers, leave weights out: Liora fills them in from last time.
3. Pick exercises with liora_search_exercises and use the catalog names (or ids) exactly. Define an exercise in
   customExercises only when nothing in the catalog is it.
4. Check the file with liora_check_routine and fix every error; read the warnings and "What Liora makes of it".
5. Save it with liora_save_routine (or write it yourself as <name>.liora.json), then tell the person how to import
   it: get the file onto the phone and open it with Liora, or share or paste the JSON into Train › Import routines.`;

const ROUTINE = {
  type: 'object',
  description:
    'The routine file as a JSON object: {"format": "liora-routine", "version": 1, "weightUnit": "kg", "routines": [...]}. See liora_routine_format.',
  additionalProperties: true,
};
const BACKUP = {
  type: 'string',
  description: "Optional: path to the person's Liora backup (.zip or liora.json), so names match their own exercises too.",
};

const TOOLS = [
  {
    name: 'liora_routine_format',
    title: 'Liora routine format',
    description: "Liora's routine file format, with examples: read it before writing a routine.",
    inputSchema: { type: 'object', properties: {} },
    annotations: { readOnlyHint: true },
  },
  {
    name: 'liora_search_exercises',
    title: 'Search exercises',
    description:
      "Searches Liora's exercise catalog (about 800, English and German names). Use the names or ids it returns in routine files. " +
      'Leave the query empty to list by equipment, muscle or tracking. Common exercises come first.',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: 'Words of the name in any order, e.g. "incline dumbbell press"; typos are fine.' },
        equipment: { type: 'string', enum: EQUIPMENT },
        muscle: { type: 'string', enum: MUSCLES },
        tracking: { type: 'string', enum: Object.keys(TRACKING) },
        limit: { type: 'integer', minimum: 1, maximum: 50, description: 'How many to return; 15 by default.' },
      },
    },
    annotations: { readOnlyHint: true },
  },
  {
    name: 'liora_exercise_details',
    title: 'Exercise details',
    description: 'One catalog exercise in full: names, aliases, tracking, equipment, muscles and instructions.',
    inputSchema: {
      type: 'object',
      properties: { exercise: { type: 'string', description: 'Its name or id.' } },
      required: ['exercise'],
    },
    annotations: { readOnlyHint: true },
  },
  {
    name: 'liora_check_routine',
    title: 'Check a routine file',
    description:
      'Checks a routine file the way Liora reads it: errors that keep it from importing, warnings, and what Liora makes of ' +
      'each exercise (catalog match, or a new exercise). Fix every error before handing the file over.',
    inputSchema: { type: 'object', properties: { routine: ROUTINE, backup: BACKUP }, required: ['routine'] },
    annotations: { readOnlyHint: true },
  },
  {
    name: 'liora_save_routine',
    title: 'Save a routine file',
    description:
      'Checks a routine file and, if it has no errors, saves it as <name>.liora.json for the person to import into Liora. ' +
      "Returns where it is. Without a path it goes into the person's Downloads folder.",
    inputSchema: {
      type: 'object',
      properties: {
        routine: ROUTINE,
        path: { type: 'string', description: 'Where to save it: a file, or a folder for <name>.liora.json.' },
        backup: BACKUP,
      },
      required: ['routine'],
    },
    annotations: { readOnlyHint: false, destructiveHint: false },
  },
  {
    name: 'liora_read_backup',
    title: "Read a person's Liora backup",
    description:
      "Reads a Liora backup (Settings › Your data › Back up everything: a .zip, or its liora.json): the person's units, gyms, " +
      'own exercises, routines and how their training has gone lately, with estimated 1RMs. Use it to fit weights and exercises to them.',
    inputSchema: {
      type: 'object',
      properties: { path: { type: 'string', description: 'Path to the backup.' } },
      required: ['path'],
    },
    annotations: { readOnlyHint: true },
  },
];

export async function serveMcp({ version }) {
  const catalogs = new Map();
  /** The catalog, with the person's own exercises when a backup is given. */
  const catalogFor = async (backupPath) => {
    const key = backupPath ? resolve(backupPath) : '';
    if (!catalogs.has(key)) {
      catalogs.set(key, loadCatalog({ own: backupPath ? ownExercises(readBackup(key)) : [] }));
    }
    return catalogs.get(key);
  };

  const call = {
    liora_routine_format: async () => repoFile('docs/routine-format.md'),
    liora_search_exercises: async ({ query = '', equipment, muscle, tracking, limit = 15 }) => {
      const catalog = await catalogFor();
      const hits = catalog.search(query, { equipment, muscle, tracking, limit: Math.min(Number(limit) || 15, 50) });
      return hits.length ? hits.map(exerciseLine).join('\n') : 'Nothing found. Try fewer or other words, or another language.';
    },
    liora_exercise_details: async ({ exercise }) => {
      const catalog = await catalogFor();
      const found = catalog.find(String(exercise ?? ''));
      if (!found) throw new Error(`No exercise "${exercise}" in the catalog. Search for it with liora_search_exercises.`);
      return exerciseDetails(found);
    },
    liora_check_routine: async ({ routine, backup }) => {
      const check = checkRoutineFile(routine, await catalogFor(backup));
      return { text: report(check), isError: !check.ok };
    },
    liora_save_routine: async ({ routine, path, backup }) => {
      const check = checkRoutineFile(routine, await catalogFor(backup));
      if (!check.ok) return { text: `Not saved.\n\n${report(check)}`, isError: true };
      const file = savePath(path, check.plan);
      mkdirSync(dirname(file), { recursive: true });
      const document = typeof routine === 'string' ? JSON.parse(routine.slice(routine.indexOf('{'), routine.lastIndexOf('}') + 1)) : routine;
      writeFileSync(file, `${JSON.stringify(document, null, 2)}\n`);
      return (
        `Saved ${file}\n\nTo import it, the person gets the file onto their phone (Quick Share, Google Drive, email, a cable) ` +
        'and opens it with Liora, or in Liora taps Train › the import button › Open file. Pasting the JSON there works too.\n\n' +
        report(check)
      );
    },
    liora_read_backup: async ({ path }) => {
      const backup = readBackup(resolve(String(path)));
      return profile(backup, await loadCatalog({ own: ownExercises(backup) }));
    },
  };

  const send = (message) => process.stdout.write(`${JSON.stringify({ jsonrpc: '2.0', ...message })}\n`);

  const handle = async (message) => {
    const { id, method, params } = message;
    const isRequest = id !== undefined && id !== null;
    try {
      switch (method) {
        case 'initialize': {
          const asked = params?.protocolVersion;
          return send({
            id,
            result: {
              protocolVersion: typeof asked === 'string' ? asked : LATEST,
              capabilities: { tools: {} },
              serverInfo: { name: 'liora-routine', title: 'Liora routines', version },
              instructions: INSTRUCTIONS,
            },
          });
        }
        case 'ping':
          return send({ id, result: {} });
        case 'tools/list':
          return send({ id, result: { tools: TOOLS } });
        case 'tools/call': {
          const tool = call[params?.name];
          if (!tool) return send({ id, error: { code: -32602, message: `Unknown tool: ${params?.name}` } });
          try {
            const output = await tool(params.arguments ?? {});
            const { text, isError = false } = typeof output === 'string' ? { text: output } : output;
            return send({ id, result: { content: [{ type: 'text', text }], isError } });
          } catch (error) {
            return send({ id, result: { content: [{ type: 'text', text: error.message }], isError: true } });
          }
        }
        default:
          if (isRequest) send({ id, error: { code: -32601, message: `Method not found: ${method}` } });
          // Notifications (initialized, cancelled, …) need no answer.
          return undefined;
      }
    } catch (error) {
      if (isRequest) send({ id, error: { code: -32603, message: error.message } });
      return undefined;
    }
  };

  const lines = createInterface({ input: process.stdin, crlfDelay: Infinity });
  const pending = new Set();
  for await (const line of lines) {
    if (!line.trim()) continue;
    let message;
    try {
      message = JSON.parse(line);
    } catch {
      send({ id: null, error: { code: -32700, message: 'Parse error' } });
      continue;
    }
    for (const one of Array.isArray(message) ? message : [message]) {
      const work = handle(one).finally(() => pending.delete(work));
      pending.add(work);
    }
  }
  await Promise.all(pending);
}

/** Where to save: [path] as a file or a folder, else the person's Downloads folder, named for the plan. */
function savePath(path, plan) {
  const name = `${slug(plan.folder?.name ?? plan.routines[0]?.name ?? plan.customExercises[0]?.name ?? 'routine')}.liora.json`;
  if (path) {
    const target = resolve(path);
    return target.endsWith('.json') ? target : join(target, name);
  }
  const downloads = join(homedir(), 'Downloads');
  return join(existsSync(downloads) ? downloads : process.cwd(), name);
}

const slug = (text) =>
  text
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '') || 'routine';
