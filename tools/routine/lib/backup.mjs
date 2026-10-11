// A person's Liora backup (Settings › Your data › Back up everything), read so an AI can plan around what they
// have: their units, gyms, own exercises, routines and how their training has gone. The format is public
// (core/data/.../backup/BackupFormat.kt); a backup is a ZIP with liora.json in it, or that JSON on its own.

import { readFileSync } from 'node:fs';
import { inflateRawSync } from 'node:zlib';

const KG_PER_LB = 0.45359237;
const DAY_MS = 24 * 60 * 60 * 1000;
/** How far back "lately" reaches: long enough for a training block, short enough to be today's strength. */
const RECENT_DAYS = 84;

/** The backup at [path]: a ZIP from Liora, or the liora.json inside one. */
export function readBackup(path) {
  const bytes = readFileSync(path);
  const text = bytes[0] === 0x50 && bytes[1] === 0x4b ? zipEntry(bytes, 'liora.json') : bytes.toString('utf8');
  const backup = JSON.parse(text);
  if (backup.format !== 'liora-backup') throw new Error(`${path} isn't a Liora backup.`);
  return backup;
}

/** The file [name] from a ZIP archive, as text. */
function zipEntry(zip, name) {
  // The end of central directory record sits within the last 64 KiB, after any comment.
  let end = zip.length - 22;
  while (end >= Math.max(0, zip.length - 65_557) && zip.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < 0 || zip.readUInt32LE(end) !== 0x06054b50) throw new Error('Not a ZIP archive.');
  const entries = zip.readUInt16LE(end + 10);
  let at = zip.readUInt32LE(end + 16);
  for (let i = 0; i < entries; i++) {
    const method = zip.readUInt16LE(at + 10);
    const size = zip.readUInt32LE(at + 20);
    const nameLength = zip.readUInt16LE(at + 28);
    const extraLength = zip.readUInt16LE(at + 30);
    const commentLength = zip.readUInt16LE(at + 32);
    const offset = zip.readUInt32LE(at + 42);
    const entryName = zip.toString('utf8', at + 46, at + 46 + nameLength);
    if (entryName === name) {
      const start = offset + 30 + zip.readUInt16LE(offset + 26) + zip.readUInt16LE(offset + 28);
      const data = zip.subarray(start, start + size);
      return (method === 0 ? data : inflateRawSync(data)).toString('utf8');
    }
    at += 46 + nameLength + extraLength + commentLength;
  }
  throw new Error(`No ${name} in the archive: is it a Liora backup?`);
}

/** The person's own exercises, as catalog entries, so names in a routine file match them like Liora does. */
export function ownExercises(backup) {
  return (backup.exercises ?? [])
    .filter((e) => !e.deletedAt)
    .map((e) => ({
      id: e.id,
      name: e.name,
      names: {},
      aliases: [],
      tracking: e.trackingType,
      equipment: e.equipment,
      category: e.category,
      primary: e.primaryMuscles ?? [],
      secondary: e.secondaryMuscles ?? [],
      instructions: [],
      rank: null,
      custom: true,
      variationOf: e.variationOf ?? null,
    }));
}

/** What an AI needs to know to plan for this person, as text. */
export function profile(backup, catalog, now = Date.now()) {
  const unit = preference(backup, 'units.weight') === 'lb' ? 'lb' : 'kg';
  const weight = (kg) => `${round(unit === 'lb' ? kg / KG_PER_LB : kg)} ${unit}`;
  const nameOf = (id) => catalog.byId.get(id)?.name ?? `(unknown exercise ${id})`;
  const lines = [
    `Liora backup made ${day(backup.exportedAt)}.`,
    `Weights in ${unit}: write the routine file with "weightUnit": "${unit}".`,
  ];

  const gyms = (backup.gyms ?? []).filter((g) => !g.deletedAt);
  if (gyms.length) {
    lines.push('', 'Gyms (what weights they can load):');
    for (const gym of gyms) lines.push(`  ${gym.name}: ${gymText(gym)}`);
  }

  const settings = new Map((backup.exerciseSettings ?? []).filter((s) => !s.deletedAt).map((s) => [s.exerciseId, s]));
  const own = ownExercises(backup);
  if (own.length) {
    lines.push('', 'Their own exercises (name them as written here, or by id; never define them again):');
    for (const e of own) {
      const base = e.variationOf ? `, a variation of ${nameOf(e.variationOf)}` : '';
      lines.push(`  ${e.name} [${e.id}] ${e.tracking}, ${e.equipment}${base}${noteText(settings.get(e.id))}`);
    }
  }

  const folders = new Map((backup.routineFolders ?? []).filter((f) => !f.deletedAt).map((f) => [f.id, f]));
  const routines = (backup.routines ?? []).filter((r) => !r.deletedAt).sort((a, b) => a.position - b.position);
  if (routines.length) {
    lines.push('', 'Their routines now:');
    for (const routine of routines) {
      const folder = folders.get(routine.folderId);
      const where = folder ? ` (in ${folder.rotation ? 'rotation' : 'folder'} "${folder.name}")` : '';
      lines.push(`  ${routine.name}${where}`);
      const exercises = (routine.exercises ?? []).filter((e) => !e.deletedAt).sort((a, b) => a.position - b.position);
      for (const exercise of exercises) {
        const sets = (exercise.sets ?? []).filter((s) => !s.deletedAt).sort((a, b) => a.position - b.position);
        const extras = [
          exercise.supersetGroup ? `superset ${exercise.supersetGroup}` : null,
          exercise.restSec !== undefined && exercise.restSec !== null ? `rest ${exercise.restSec} s` : null,
          exercise.progression ? `${exercise.progression} progression` : null,
        ].filter(Boolean);
        lines.push(`    ${nameOf(exercise.exerciseId)}: ${plannedText(sets, weight)}${extras.length ? ` · ${extras.join(' · ')}` : ''}`);
      }
    }
  }

  lines.push('', ...trainingText(backup, nameOf, weight, now));

  const bodyweight = (backup.measurements ?? [])
    .filter((m) => m.type === 'bodyweight' && !m.deletedAt)
    .sort((a, b) => Date.parse(b.takenAt) - Date.parse(a.takenAt))[0];
  if (bodyweight) lines.push('', `Bodyweight: ${weight(bodyweight.value)} on ${day(bodyweight.takenAt)}.`);
  return lines.join('\n');
}

/** How training has gone lately, exercise by exercise, the most trained first. */
function trainingText(backup, nameOf, weight, now) {
  const workouts = (backup.workouts ?? []).filter((w) => !w.deletedAt);
  if (workouts.length === 0) return ['No workouts logged yet: leave weights out of the routines, Liora fills them in from the first sessions.'];
  const since = now - RECENT_DAYS * DAY_MS;
  const recent = workouts.filter((w) => Date.parse(w.startedAt) >= since);
  const last = workouts.map((w) => Date.parse(w.startedAt)).sort((a, b) => b - a)[0];
  const lines = [
    `Training: ${workouts.length} workouts logged, ${recent.length} in the last ${RECENT_DAYS / 7} weeks ` +
      `(${round(recent.length / (RECENT_DAYS / 7))} a week); the last on ${day(last)}.`,
  ];
  const byExercise = new Map();
  for (const workout of recent) {
    for (const exercise of workout.exercises ?? []) {
      if (exercise.deletedAt) continue;
      const sets = (exercise.sets ?? []).filter((s) => !s.deletedAt && s.completedAt && s.setType !== 'warmup');
      if (sets.length === 0) continue;
      const entry = byExercise.get(exercise.exerciseId) ?? { sessions: [], last: 0 };
      entry.sessions.push({ at: Date.parse(workout.startedAt), sets });
      entry.last = Math.max(entry.last, Date.parse(workout.startedAt));
      byExercise.set(exercise.exerciseId, entry);
    }
  }
  if (byExercise.size === 0) return lines;
  lines.push(
    'Exercises lately (sessions, last time, and for weight × reps the best estimated 1RM, Epley, and the last session\'s top set):',
  );
  const rows = [...byExercise].sort((a, b) => b[1].sessions.length - a[1].sessions.length || b[1].last - a[1].last);
  for (const [id, entry] of rows.slice(0, 40)) {
    const latest = entry.sessions.sort((a, b) => b.at - a.at)[0];
    const loaded = (set) => set.weightKg && set.reps;
    const best = Math.max(0, ...entry.sessions.flatMap((s) => s.sets.filter(loaded).map((set) => e1rm(set.weightKg, set.reps))));
    const top = latest.sets.filter(loaded).sort((a, b) => b.weightKg - a.weightKg || b.reps - a.reps)[0];
    const sets = latest.sets.map((set) => setText(set, weight)).join(', ');
    const facts = [
      `${entry.sessions.length}×, last ${day(entry.last)}`,
      best ? `e1RM ${weight(best)}` : null,
      top ? `top set ${weight(top.weightKg)} × ${top.reps}` : null,
    ].filter(Boolean);
    lines.push(`  ${nameOf(id)} [${id}]: ${facts.join(' · ')}`);
    lines.push(`    last session: ${sets}`);
  }
  return lines;
}

const e1rm = (kg, reps) => (reps <= 1 ? kg : kg * (1 + reps / 30));

function setText(set, weight) {
  const parts = [];
  if (set.weightKg) parts.push(weight(set.weightKg));
  if (set.reps) parts.push(`${set.reps}`);
  if (set.durationSec) parts.push(`${set.durationSec} s`);
  if (set.distanceM) parts.push(`${round(set.distanceM)} m`);
  return `${set.setType === 'normal' ? '' : `${set.setType} `}${parts.join(' × ')}${set.rpe ? ` @${set.rpe}` : ''}`;
}

function plannedText(sets, weight) {
  if (sets.length === 0) return 'no sets';
  const groups = [];
  for (const text of sets.map((set) => plannedSetText(set, weight))) {
    if (groups.at(-1)?.text === text) groups.at(-1).count++;
    else groups.push({ text, count: 1 });
  }
  return groups.map((g) => `${g.count > 1 ? `${g.count}× ` : ''}${g.text}`).join(', ');
}

function plannedSetText(set, weight) {
  const reps = set.targetRepsMin
    ? set.targetRepsMax && set.targetRepsMax !== set.targetRepsMin
      ? `${set.targetRepsMin}–${set.targetRepsMax}`
      : `${set.targetRepsMin}`
    : null;
  const parts = [
    set.targetWeightKg ? weight(set.targetWeightKg) : null,
    reps,
    set.targetDurationSec ? `${set.targetDurationSec} s` : null,
    set.targetDistanceM ? `${round(set.targetDistanceM)} m` : null,
  ].filter(Boolean);
  return `${set.setType === 'normal' ? '' : `${set.setType} `}${parts.join(' × ') || 'open'}`;
}

function gymText(gym) {
  const own = (kg) => `${round(gym.unit === 'lb' ? kg / KG_PER_LB : kg)}`;
  const plates = (gym.plates ?? []).map((p) => `${own(p.kg)}×${p.pairs * 2}`).join(', ');
  const dumbbells = (gym.dumbbells ?? []).map((d) => `${own(d.fromKg)}–${own(d.toKg)} by ${own(d.stepKg)}`).join(', ');
  return [
    `in ${gym.unit}`,
    `barbell ${own(gym.barbellKg)}, EZ bar ${own(gym.ezBarKg)}`,
    plates ? `plates ${plates}` : null,
    dumbbells ? `dumbbells ${dumbbells}` : null,
    `machine stacks in steps of ${own(gym.stackStepKg)}`,
  ]
    .filter(Boolean)
    .join('; ');
}

function noteText(settings) {
  const parts = [settings?.stickyNote ? `note: "${settings.stickyNote}"` : null, settings?.loadStepKg ? `own load step ${settings.loadStepKg} kg` : null];
  const text = parts.filter(Boolean).join(', ');
  return text ? ` (${text})` : '';
}

const preference = (backup, key) => (backup.preferences ?? []).find((p) => p.key === key && !p.deletedAt)?.value;
const round = (value) => Math.round(value * 10) / 10;
const day = (at) => new Date(typeof at === 'number' ? at : Date.parse(at)).toISOString().slice(0, 10);
