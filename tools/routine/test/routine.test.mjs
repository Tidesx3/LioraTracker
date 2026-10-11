// node --test tools/routine
// The routine tool against the real catalog and the examples in docs/routine-format.md, so the documentation,
// the tool and the app (RoutineImportFlowTest reads the same examples) agree.

import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { deflateRawSync } from 'node:zlib';
import { ownExercises, profile, readBackup } from '../lib/backup.mjs';
import { loadCatalog, repoRoot } from '../lib/catalog.mjs';
import { checkRoutineFile, meters, repRange, report, rpe, seconds } from '../lib/check.mjs';

const TOOL = join(repoRoot, 'tools', 'routine', 'liora-routine.mjs');
const DOCS = readFileSync(join(repoRoot, 'docs', 'routine-format.md'), 'utf8');
const [SMALL, PROGRAM] = [...DOCS.matchAll(/```json\n([\s\S]*?)```/g)].map((match) => match[1]);
const catalog = await loadCatalog();

test('the examples in the documentation import cleanly', () => {
  for (const example of [SMALL, PROGRAM]) {
    const check = checkRoutineFile(example, catalog);
    assert.deepEqual(check.errors, []);
    assert.deepEqual(check.warnings, []);
  }
  const program = checkRoutineFile(PROGRAM, catalog);
  assert.deepEqual(
    program.exercises.map((e) => [e.name, e.kind, e.exercise?.id ?? e.basedOn?.id]),
    [
      ['Barbell Bench Press', 'catalog', 'fedb.Barbell_Bench_Press_-_Medium_Grip'],
      ['Chest-Supported Dumbbell Row', 'catalog', 'liora.chest_supported_dumbbell_row'],
      ['Cable Y-Raise', 'custom', 'liora.cable_lateral_raise'],
      ['Triceps Pushdown', 'catalog', 'fedb.Triceps_Pushdown'],
      ['Barbell Deadlift', 'catalog', 'fedb.Barbell_Deadlift'],
      ["Farmer's Walk", 'catalog', 'fedb.Farmers_Walk'],
      ['Stationary Bike', 'catalog', 'fedb.Bicycling_Stationary'],
    ],
  );
  assert.match(report(program), /Ready to import: 2 routines in a new rotation "Upper \/ Lower", 1 new exercise/);
});

test('names match as Liora matches them', () => {
  const match = (name, fields = {}) => {
    const suggestion = catalog.suggestPlanned(name, fields);
    return suggestion.existing ? suggestion.existing.id : `new:${suggestion.closest?.id}`;
  };
  assert.equal(match('Bench Press (Barbell)'), 'fedb.Barbell_Bench_Press_-_Medium_Grip');
  assert.equal(match('Bankdrücken (Langhantel)'), 'fedb.Barbell_Bench_Press_-_Medium_Grip');
  // Weight on a bodyweight exercise: its weighted variant.
  assert.equal(match('Pull-Up', { weight: true, reps: true }), 'fedb.Weighted_Pull_Ups');
  // A plan's weight on a plank is left out rather than making a second plank.
  assert.equal(match('Plank', { weight: true, duration: true }), 'fedb.Plank');
  // A gym's own machine becomes a variation of the closest.
  assert.equal(match('Lat Pulldown Studio North', { reps: true }), 'new:fedb.Wide-Grip_Lat_Pulldown');
});

test('search puts the common exercise first', () => {
  assert.equal(catalog.search('bench')[0].name, 'Barbell Bench Press');
  assert.equal(catalog.search('kreuzhebn')[0].name, 'Barbell Deadlift');
  assert.ok(catalog.search('', { muscle: 'glutes', equipment: 'machine' }).every((e) => e.equipment === 'machine'));
});

test('problems say where they are and how to fix them', () => {
  const check = checkRoutineFile(
    `{"format": "liora-routine", "version": 1, "routines": [
      {"exercises": [{"exercise": "Curl", "sets": 2}]},
      {"name": "Push", "exercises": [
        {"exercise": "Bench Press", "sets": [{"weight": 80, "reps": "eight"}, {"type": "superset", "rpe": 8.3}]},
        {"sets": 3},
        {"exercise": "Dips", "sets": 0},
        {"exercise": "Fly"}
      ]}
    ],
    "customExercises": [{"name": "Thing"}, {"name": "thing", "tracking": "weights"}]}`,
    catalog,
  );
  assert.equal(check.ok, false);
  assert.deepEqual(
    check.errors.map((e) => e.where).sort(),
    [
      'customExercises[0] "Thing" › tracking',
      'customExercises[1] "thing" › tracking',
      'customExercises',
      'routines[0] › name',
      'routines[1] "Push" › exercises[0] "Bench Press" › sets[0] › weight',
      'routines[1] "Push" › exercises[0] "Bench Press" › sets[0] › reps',
      'routines[1] "Push" › exercises[1] › exercise',
      'routines[1] "Push" › exercises[2] "Dips" › sets',
      'routines[1] "Push" › exercises[3] "Fly" › sets',
      'routines[1] "Push" › exercises[0] "Bench Press" › sets[1] › type',
      'routines[1] "Push" › exercises[0] "Bench Press" › sets[1] › rpe',
    ].sort(),
  );
  const message = (where) => check.errors.find((e) => e.where === where).message;
  assert.match(message('routines[1] "Push" › exercises[0] "Bench Press" › sets[0] › weight'), /"weightUnit": "kg"/);
  assert.match(message('routines[1] "Push" › exercises[0] "Bench Press" › sets[0] › reps'), /"type": "failure"/);
});

test('what Liora leaves out is a warning', () => {
  const check = checkRoutineFile(
    `{"format": "liora-routine", "version": 1, "weightUnit": "kg", "author": "an AI", "routines": [{"name": "A", "exercises": [
      {"exercise": "Bench Press", "superset": "S", "tempo": "3-1-1", "sets": 3, "reps": 8, "progression": "double"},
      {"exercise": "Plank", "sets": 2, "duration": 60, "weight": 10, "progression": "double"},
      {"exercise": "Cable Fly", "superset": "S", "sets": 3, "reps": "10-15"}
    ]}]}`,
    catalog,
  );
  assert.equal(check.ok, true);
  const warnings = check.warnings.map((w) => `${w.where}: ${w.message}`).join('\n');
  assert.match(warnings, /^file: Liora doesn't know "author"/m);
  assert.match(warnings, /"Bench Press": Liora doesn't know "tempo"/);
  assert.match(warnings, /"Bench Press": Double progression needs a rep range/);
  assert.match(warnings, /"Plank": Plank is tracked as duration, with no column for weight/);
  assert.match(warnings, /"Plank": Double progression only works with weight × reps/);
  assert.doesNotMatch(warnings, /"Plank": Double progression needs/);
  assert.match(warnings, /Superset "S" isn't side by side/);
  // As Liora saves it.
  assert.match(report(check), /Plank \[fedb.Plank\]\n\s+2× 1:00/);
});

test('values in the forms people write them', () => {
  assert.deepEqual(repRange('8–12'), { min: 8, max: 12, range: true });
  assert.deepEqual(repRange('8 to 12'), { min: 8, max: 12, range: true });
  assert.deepEqual(repRange(10), { min: 10, max: 10, range: false });
  assert.equal(repRange('0'), null);
  assert.equal(seconds('1:30'), 90);
  assert.equal(seconds('1:00:00'), 3600);
  assert.equal(seconds('90s'), 90);
  assert.equal(seconds('2 min'), 120);
  assert.equal(seconds('1:75'), null);
  assert.equal(meters('5 km'), 5000);
  assert.equal(meters('1 mi'), 1609.344);
  assert.equal(meters(400), 400);
  assert.equal(rpe(7.5), 7.5);
  assert.equal(rpe(8.3), null);
  const check = checkRoutineFile(
    `{"format": "liora-routine", "version": 1, "weightUnit": "lb", "routines": [{"name": "A", "exercises": [
      {"exercise": "Bench Press", "reps": "6-8", "weight": 185, "sets": [{"type": "warmup", "weight": "60 kg", "reps": 5}, {"count": 3}]}
    ]}]}`,
    catalog,
  );
  const [warmup, working] = check.plan.routines[0].exercises[0].sets;
  assert.equal(warmup.weightKg, 60);
  assert.equal(check.plan.routines[0].exercises[0].sets.length, 4);
  assert.ok(Math.abs(working.weightKg - 83.91) < 0.01);
  assert.deepEqual(working.reps, { min: 6, max: 8, range: true });
});

test('an AI answer around the file, with comments and trailing commas', () => {
  const check = checkRoutineFile(
    'Here you go:\n```json\n{"format": "liora-routine", "version": 1, // hi\n "routines": [{"name": "A", "exercises": [{"exercise": "Curl", "sets": 2, "reps": 12,}]}],}\n```',
    catalog,
  );
  assert.equal(check.ok, true);
  assert.match(check.warnings[0].message, /Comments or trailing commas/);
  assert.equal(checkRoutineFile('Just do squats.', catalog).ok, false);
});

test('a backup tells the AI about the person', async () => {
  const folder = mkdtempSync(join(tmpdir(), 'liora-routine-'));
  const zip = join(folder, 'liora-backup.zip');
  writeFileSync(zip, zipOf('liora.json', JSON.stringify(BACKUP)));
  const backup = readBackup(zip);
  const text = profile(backup, await loadCatalog({ own: ownExercises(backup) }), Date.parse('2026-10-11T12:00:00Z'));
  assert.match(text, /Weights in lb: write the routine file with "weightUnit": "lb"/);
  assert.match(text, /Home: in lb; barbell 45, EZ bar 25; plates 45×4, 25×2/);
  assert.match(text, /Leg Press Studio North \[0190aaaa-0000-7000-8000-000000000001\] weight_reps, machine, a variation of Leg Press \(note: "Seat on 4"\)/);
  assert.match(text, /Push \(in rotation "PPL"\)\n {4}Barbell Bench Press: 3× 6–8 · rest 180 s · double progression/);
  assert.match(text, /Training: 2 workouts logged, 2 in the last 12 weeks/);
  assert.match(text, /Barbell Bench Press \[fedb.Barbell_Bench_Press_-_Medium_Grip\]: 2×, last 2026-10-09 · e1RM 234.3 lb · top set 185 lb × 7/);
  assert.match(text, /Bodyweight: 180 lb on 2026-10-08/);

  // Their own exercise's name matches theirs, so it isn't made twice.
  const own = await loadCatalog({ own: ownExercises(backup) });
  const check = checkRoutineFile(
    '{"format": "liora-routine", "version": 1, "routines": [{"name": "Legs", "exercises": [{"exercise": "Leg Press Studio North", "sets": 3, "reps": 10}]}]}',
    own,
  );
  assert.equal(check.exercises[0].kind, 'yours');
});

test('the command line', () => {
  const folder = mkdtempSync(join(tmpdir(), 'liora-routine-'));
  writeFileSync(join(folder, 'good.liora.json'), SMALL);
  writeFileSync(join(folder, 'bad.liora.json'), '{"format": "liora-routine", "version": 1, "routines": []}');
  const run = (...args) => spawnSync(process.execPath, [TOOL, ...args], { encoding: 'utf8' });
  assert.equal(run('check', join(folder, 'good.liora.json')).status, 0);
  const bad = run('check', join(folder, 'bad.liora.json'), '--json');
  assert.equal(bad.status, 1);
  assert.equal(JSON.parse(bad.stdout).ok, false);
  assert.match(run('search', 'bench', '--limit', '1').stdout, /^Barbell Bench Press \[fedb.Barbell_Bench_Press_-_Medium_Grip\]/);
  assert.equal(run('nonsense').status, 2);
  assert.equal(run('format').stdout, DOCS);
});

test('the MCP server', async () => {
  const folder = mkdtempSync(join(tmpdir(), 'liora-routine-'));
  const server = spawn(process.execPath, [TOOL, 'mcp'], { stdio: ['pipe', 'pipe', 'inherit'] });
  const answers = new Map();
  let buffer = '';
  server.stdout.on('data', (chunk) => {
    buffer += chunk;
    for (let end = buffer.indexOf('\n'); end >= 0; end = buffer.indexOf('\n')) {
      const message = JSON.parse(buffer.slice(0, end));
      buffer = buffer.slice(end + 1);
      answers.get(message.id)?.(message);
    }
  });
  let id = 0;
  const ask = (method, params) =>
    new Promise((resolve) => {
      answers.set(++id, resolve);
      server.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`);
    });
  const call = async (name, args) => (await ask('tools/call', { name, arguments: args })).result;

  const init = await ask('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'test', version: '1' } });
  assert.equal(init.result.protocolVersion, '2025-06-18');
  assert.match(init.result.instructions, /liora_routine_format/);
  server.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' })}\n`);

  const tools = (await ask('tools/list', {})).result.tools.map((t) => t.name);
  assert.deepEqual(tools, [
    'liora_routine_format',
    'liora_search_exercises',
    'liora_exercise_details',
    'liora_check_routine',
    'liora_save_routine',
    'liora_read_backup',
  ]);
  assert.match((await call('liora_search_exercises', { query: 'bench', limit: 3 })).content[0].text, /^Barbell Bench Press/);
  assert.match((await call('liora_exercise_details', { exercise: 'Kniebeuge' })).content[0].text, /Barbell Squat/);
  assert.equal((await call('liora_routine_format', {})).content[0].text, DOCS);

  const broken = await call('liora_check_routine', { routine: { format: 'liora-routine', version: 1, routines: [] } });
  assert.equal(broken.isError, true);
  const notSaved = await call('liora_save_routine', { routine: { format: 'liora-routine', version: 1, routines: [] }, path: folder });
  assert.equal(notSaved.isError, true);

  const saved = await call('liora_save_routine', { routine: JSON.parse(PROGRAM), path: folder });
  assert.equal(saved.isError, false);
  const file = join(folder, 'upper-lower.liora.json');
  assert.ok(existsSync(file), saved.content[0].text);
  assert.deepEqual(JSON.parse(readFileSync(file, 'utf8')), JSON.parse(PROGRAM));

  assert.equal((await ask('nothing/here', {})).error.code, -32601);
  server.stdin.end();
  await new Promise((resolve) => server.on('close', resolve));
});

/** A ZIP archive holding [name] with [text], deflated like Liora's backups. */
function zipOf(name, text) {
  const data = deflateRawSync(Buffer.from(text));
  const nameBytes = Buffer.from(name);
  const local = Buffer.alloc(30);
  local.writeUInt32LE(0x04034b50, 0);
  local.writeUInt16LE(20, 4);
  local.writeUInt16LE(8, 8);
  local.writeUInt32LE(data.length, 18);
  local.writeUInt32LE(Buffer.byteLength(text), 22);
  local.writeUInt16LE(nameBytes.length, 26);
  const central = Buffer.alloc(46);
  central.writeUInt32LE(0x02014b50, 0);
  central.writeUInt16LE(20, 4);
  central.writeUInt16LE(20, 6);
  central.writeUInt16LE(8, 10);
  central.writeUInt32LE(data.length, 20);
  central.writeUInt32LE(Buffer.byteLength(text), 24);
  central.writeUInt16LE(nameBytes.length, 28);
  central.writeUInt32LE(0, 42);
  const centralOffset = local.length + nameBytes.length + data.length;
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(1, 8);
  end.writeUInt16LE(1, 10);
  end.writeUInt32LE(central.length + nameBytes.length, 12);
  end.writeUInt32LE(centralOffset, 16);
  return Buffer.concat([local, nameBytes, data, central, nameBytes, end]);
}

const stamp = { createdAt: '2026-09-01T10:00:00Z', hlc: '0000000000001:0000:phone' };
const BACKUP = {
  format: 'liora-backup',
  version: 1,
  exportedAt: '2026-10-11T08:00:00Z',
  exercises: [
    {
      id: '0190aaaa-0000-7000-8000-000000000001',
      name: 'Leg Press Studio North',
      trackingType: 'weight_reps',
      equipment: 'machine',
      category: 'strength',
      primaryMuscles: ['quadriceps'],
      variationOf: 'fedb.Leg_Press',
      ...stamp,
    },
  ],
  exerciseSettings: [{ exerciseId: '0190aaaa-0000-7000-8000-000000000001', stickyNote: 'Seat on 4', ...stamp }],
  routineFolders: [{ id: 'f1', name: 'PPL', position: 0, rotation: true, ...stamp }],
  routines: [
    {
      id: 'r1',
      folderId: 'f1',
      name: 'Push',
      position: 0,
      ...stamp,
      exercises: [
        {
          id: 're1',
          exerciseId: 'fedb.Barbell_Bench_Press_-_Medium_Grip',
          position: 0,
          restSec: 180,
          progression: 'double',
          ...stamp,
          sets: [0, 1, 2].map((position) => ({
            id: `rs${position}`,
            position,
            setType: 'normal',
            targetRepsMin: 6,
            targetRepsMax: 8,
            ...stamp,
          })),
        },
      ],
    },
  ],
  workouts: ['2026-10-02T17:00:00Z', '2026-10-09T17:00:00Z'].map((startedAt, index) => ({
    id: `w${index}`,
    name: 'Push',
    startedAt,
    endedAt: startedAt.replace('17:', '18:'),
    routineId: 'r1',
    ...stamp,
    exercises: [
      {
        id: `we${index}`,
        exerciseId: 'fedb.Barbell_Bench_Press_-_Medium_Grip',
        position: 0,
        ...stamp,
        sets: [
          { id: `ws${index}a`, position: 0, setType: 'warmup', weightKg: 40, reps: 10, completedAt: startedAt, ...stamp },
          { id: `ws${index}b`, position: 1, setType: 'normal', weightKg: 83.91, reps: 8 - index, completedAt: startedAt, ...stamp },
        ],
      },
    ],
  })),
  measurements: [{ id: 'm1', takenAt: '2026-10-08T07:00:00Z', type: 'bodyweight', value: 81.6466, ...stamp }],
  gyms: [
    {
      id: 'g1',
      name: 'Home',
      unit: 'lb',
      barbellKg: 20.4117,
      ezBarKg: 11.3398,
      plates: [{ kg: 20.4117, pairs: 2 }, { kg: 11.3398, pairs: 1 }],
      dumbbells: [],
      stackStepKg: 4.5359,
      ...stamp,
    },
  ],
  preferences: [{ key: 'units.weight', value: 'lb', ...stamp }],
};
