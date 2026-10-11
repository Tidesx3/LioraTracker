#!/usr/bin/env node
// Makes Liora routines with any AI: a command line for AIs that run commands, and an MCP server for those that
// call tools (Claude Code, Codex, Gemini CLI, Cursor, Claude Desktop, …). No dependencies; Node 18 or later.
//
//   node tools/routine/liora-routine.mjs format               the routine file format, for the AI to read
//   node tools/routine/liora-routine.mjs search <words…>      catalog exercises   [--equipment k] [--muscle k]
//                                                              [--tracking k] [--limit n]
//   node tools/routine/liora-routine.mjs show <name or id>    one exercise in full
//   node tools/routine/liora-routine.mjs check <file | ->     what's wrong, and what Liora makes of it
//                                                              [--backup <file>] [--json]
//   node tools/routine/liora-routine.mjs profile <backup>     a Liora backup, summed up for planning
//   node tools/routine/liora-routine.mjs mcp                  serve the same as MCP tools over stdio
//
// The format is docs/routine-format.md; tools/routine/README.md sets it up for each AI harness.

import { readFileSync } from 'node:fs';
import { readBackup, ownExercises, profile } from './lib/backup.mjs';
import { loadCatalog, repoFile } from './lib/catalog.mjs';
import { checkRoutineFile, report } from './lib/check.mjs';
import { serveMcp } from './lib/mcp.mjs';
import { exerciseDetails, exerciseLine } from './lib/text.mjs';

const VERSION = '1.0.0';
const USAGE = `Usage: node tools/routine/liora-routine.mjs <command>

  format                    Print Liora's routine file format (read it before writing a routine)
  search <words…>           Find exercises in Liora's catalog, common ones first
                              --equipment <key> --muscle <key> --tracking <key> --limit <n>
  show <name or id>         One exercise in full: names, tracking, muscles, instructions
  check <file | ->          Check a routine file (- reads stdin): errors, warnings, and what Liora makes of it
                              --backup <file>  match the person's own exercises too
                              --json           the result as JSON
  profile <backup>          Sum up a Liora backup (.zip or liora.json): units, gyms, own exercises, routines,
                            and recent training with estimated 1RMs
  mcp                       Run as an MCP server over stdio

Exit status: 0, or 1 when check finds errors, 2 for a wrong command.`;

const [command, ...rest] = process.argv.slice(2);
const { words, options } = parseArgs(rest);

try {
  switch (command) {
    case 'format':
      process.stdout.write(await repoFile('docs/routine-format.md'));
      break;
    case 'search': {
      const catalog = await loadCatalog();
      const hits = catalog.search(words.join(' '), {
        equipment: options.equipment,
        muscle: options.muscle,
        tracking: options.tracking,
        limit: Number(options.limit ?? 15),
      });
      console.log(hits.length ? hits.map(exerciseLine).join('\n') : 'Nothing found. Try fewer or other words, or German.');
      break;
    }
    case 'show': {
      const catalog = await loadCatalog();
      const found = catalog.find(words.join(' '));
      if (!found) fail(`No exercise "${words.join(' ')}" in the catalog. Try search.`, 1);
      console.log(exerciseDetails(found));
      break;
    }
    case 'check': {
      if (words.length !== 1) usage();
      const text = words[0] === '-' ? readFileSync(0, 'utf8') : readFileSync(words[0], 'utf8');
      const own = options.backup ? ownExercises(readBackup(options.backup)) : [];
      const check = checkRoutineFile(text, await loadCatalog({ own }));
      if (options.json) {
        console.log(JSON.stringify({ ok: check.ok, errors: check.errors, warnings: check.warnings, exercises: check.exercises.map(brief) }, null, 2));
      } else {
        console.log(report(check));
      }
      process.exitCode = check.ok ? 0 : 1;
      break;
    }
    case 'profile': {
      if (words.length !== 1) usage();
      const backup = readBackup(words[0]);
      console.log(profile(backup, await loadCatalog({ own: ownExercises(backup) })));
      break;
    }
    case 'mcp':
      await serveMcp({ version: VERSION });
      break;
    case '--version':
      console.log(VERSION);
      break;
    case undefined:
    case 'help':
    case '--help':
    case '-h':
      console.log(USAGE);
      break;
    default:
      usage();
  }
} catch (error) {
  fail(error.message, 1);
}

function parseArgs(args) {
  const words = [];
  const options = {};
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg.startsWith('--')) {
      const [key, value] = arg.slice(2).split('=', 2);
      options[key] = value ?? (args[i + 1] && !args[i + 1].startsWith('--') && key !== 'json' ? args[++i] : true);
    } else {
      words.push(arg);
    }
  }
  return { words, options };
}

/** An exercise's resolution, for --json. */
function brief(exercise) {
  return {
    name: exercise.name,
    sets: exercise.sets,
    becomes: exercise.kind,
    id: exercise.exercise?.id,
    catalogName: exercise.exercise?.name,
    tracking: exercise.exercise?.tracking ?? exercise.tracking,
    variationOf: exercise.basedOn?.name,
  };
}

function usage() {
  fail(USAGE, 2);
}

function fail(message, status) {
  console.error(message);
  process.exit(status);
}
