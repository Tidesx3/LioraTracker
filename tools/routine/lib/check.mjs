// Checks a routine file the way Liora reads it (core/data/.../importing/RoutineFileReader.kt and
// RoutineImportRepository.kt), and says what Liora will make of it. The messages are for the AI that wrote the
// file: each says where the problem is and how to fix it. Keep the rules in step with the app's.

import { EQUIPMENT, MUSCLES, PROGRESSING, TRACKING, fits, trackingFor } from './catalog.mjs';

export const FORMAT = 'liora-routine';
export const VERSION = 1;

const FILE_FIELDS = new Set(['format', 'version', 'weightUnit', 'folder', 'customExercises', 'routines', '$schema']);
const FOLDER_FIELDS = new Set(['name', 'rotation']);
const CUSTOM_FIELDS = new Set([
  'name', 'variationOf', 'tracking', 'equipment', 'primaryMuscles', 'secondaryMuscles', 'note', 'notes', 'loadStep',
]);
const ROUTINE_FIELDS = new Set(['name', 'notes', 'exercises']);
const EXERCISE_FIELDS = new Set(['exercise', 'name', 'sets', 'rest', 'superset', 'progression', 'note', 'notes']);
const TARGET_FIELDS = new Set(['type', 'weight', 'reps', 'duration', 'distance', 'rpe']);
const SET_TYPES = ['warmup', 'normal', 'drop', 'failure'];
const NO_PROGRESSION = new Set(['none', 'off', 'false']);
const KG_PER_LB = 0.45359237;
const MAX_KG = 1000;

/**
 * Checks [input] (the file's text, or its parsed object) against [catalog]. Returns the errors that keep
 * Liora from importing it, warnings, the plan as Liora reads it, and what each exercise becomes.
 */
export function checkRoutineFile(input, catalog) {
  const problems = new Problems();
  const document = parse(input, problems);
  if (!document) return result(problems, null, []);
  const plan = new Reader(document, problems).read();
  if (!plan) return result(problems, null, []);
  const exercises = resolve(plan, catalog, problems);
  return result(problems, plan, exercises);
}

class Problems {
  errors = [];
  warnings = [];
  error(where, message) {
    this.errors.push({ where: where.text, message });
  }
  warn(where, message) {
    this.warnings.push({ where: where.text, message });
  }
}

/** Where in the file something is, as a path an AI can follow: routines[0] "Push" › exercises[1] "Bench". */
class Where {
  constructor(parts = []) {
    this.parts = parts;
  }
  in(key, index, name) {
    const label = index === undefined ? key : `${key}[${index}]`;
    return new Where([...this.parts, name ? `${label} "${name}"` : label]);
  }
  get text() {
    return this.parts.length ? this.parts.join(' › ') : 'file';
  }
}

function parse(input, problems) {
  if (input !== null && typeof input === 'object') return checkDocument(input, problems);
  const text = String(input ?? '');
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end < start) {
    problems.error(new Where(), 'There is no JSON object in this. A routine file is one JSON object.');
    return null;
  }
  const json = text.slice(start, end + 1);
  try {
    return checkDocument(JSON.parse(json), problems);
  } catch (error) {
    // Liora reads comments and trailing commas too, but plain JSON is what every tool reads.
    try {
      const document = JSON.parse(lenient(json));
      problems.warn(new Where(), 'Comments or trailing commas: Liora reads them, but plain JSON is safer.');
      return checkDocument(document, problems);
    } catch {
      problems.error(new Where(), `Not valid JSON: ${error.message}`);
      return null;
    }
  }
}

/** [json] without comments and trailing commas, strings left alone. */
function lenient(json) {
  let out = '';
  for (let i = 0; i < json.length; i++) {
    const char = json[i];
    if (char === '"') {
      let end = i + 1;
      while (end < json.length && json[end] !== '"') end += json[end] === '\\' ? 2 : 1;
      out += json.slice(i, end + 1);
      i = end;
    } else if (char === '/' && json[i + 1] === '/') {
      while (i < json.length && json[i] !== '\n') i++;
    } else if (char === '/' && json[i + 1] === '*') {
      i = json.indexOf('*/', i + 2) + 1 || json.length;
    } else {
      out += char;
    }
  }
  return out.replace(/,(\s*[}\]])/g, '$1');
}

function checkDocument(document, problems) {
  const here = new Where();
  if (Array.isArray(document) || typeof document !== 'object') {
    problems.error(here, 'A routine file is one JSON object, not a list or a value.');
    return null;
  }
  if (document.format !== undefined && document.format !== FORMAT) {
    problems.error(here.in('format'), `"${document.format}" isn't a routine file; it must be "${FORMAT}".`);
    return null;
  }
  const single = document.routines === undefined && Array.isArray(document.exercises);
  if (document.routines === undefined && !single && document.customExercises === undefined) {
    problems.error(here, `Not a Liora routine file: it needs "format": "${FORMAT}", "version": 1 and "routines".`);
    return null;
  }
  if (document.version !== undefined && !(Number(document.version) <= VERSION)) {
    problems.error(here.in('version'), `Version ${document.version} is newer than Liora reads; write version ${VERSION}.`);
    return null;
  }
  if (document.format === undefined || document.version === undefined) {
    problems.warn(here, `Add "format": "${FORMAT}" and "version": ${VERSION}, so the file says what it is.`);
  }
  return document;
}

/** Reads a checked document into a plan, collecting every problem rather than stopping at the first. */
class Reader {
  constructor(document, problems) {
    this.document = document;
    this.problems = problems;
    this.single = document.routines === undefined && Array.isArray(document.exercises);
    this.weightUnit = null;
  }

  read() {
    const here = new Where();
    const document = this.document;
    this.fields(document, this.single ? new Set([...FILE_FIELDS, ...ROUTINE_FIELDS]) : FILE_FIELDS, here);
    const unit = this.text(document, 'weightUnit', here);
    if (unit !== null) {
      this.weightUnit = weightUnit(unit);
      if (!this.weightUnit) this.invalid(here.in('weightUnit'), unit, 'write "kg" or "lb".');
    }
    const folder = this.folder(document.folder, here.in('folder'));
    const customExercises = this.list(document, 'customExercises', here)
      .map((element, index) => this.customExercise(element, here.in('customExercises', index)))
      .filter(Boolean);
    const seen = new Map();
    for (const custom of customExercises) {
      const key = custom.name.toLowerCase();
      if (seen.has(key)) {
        this.problems.error(here.in('customExercises'), `Two custom exercises are called "${custom.name}"; give each its own name.`);
      }
      seen.set(key, custom);
    }
    const routines = this.single
      ? [this.routine(document, here)].filter(Boolean)
      : this.list(document, 'routines', here, customExercises.length === 0)
          .map((element, index) => this.routine(element, here.in('routines', index, element?.name)))
          .filter(Boolean);
    return { weightUnit: this.weightUnit, folder, customExercises, routines };
  }

  folder(element, here) {
    if (element === undefined || element === null) return null;
    if (typeof element === 'string') return element.trim() ? { name: element.trim(), rotation: false } : null;
    if (typeof element !== 'object' || Array.isArray(element)) {
      this.invalid(here, element, 'write { "name": "…", "rotation": true } or just the name.');
      return null;
    }
    this.fields(element, FOLDER_FIELDS, here);
    const name = this.text(element, 'name', here, true);
    if (element.rotation !== undefined && element.rotation !== null && typeof element.rotation !== 'boolean') {
      this.invalid(here.in('rotation'), element.rotation, 'write true or false.');
    }
    return name ? { name, rotation: element.rotation === true } : null;
  }

  customExercise(element, here) {
    if (!isObject(element)) {
      this.invalid(here, element, 'each custom exercise is an object with at least a "name".');
      return null;
    }
    const name = this.text(element, 'name', here, true);
    if (!name) return null;
    here = new Where([...here.parts.slice(0, -1), `${here.parts.at(-1)} "${name}"`]);
    this.fields(element, CUSTOM_FIELDS, here);
    const variationOf = this.text(element, 'variationOf', here);
    const tracking = this.key(element, 'tracking', here, Object.keys(TRACKING));
    if (!tracking && !variationOf && element.tracking === undefined) {
      this.problems.error(
        here.in('tracking'),
        `Missing: say how a set is measured (${Object.keys(TRACKING).join(', ')}), or give "variationOf" to take it from a catalog exercise.`,
      );
    }
    return {
      name,
      variationOf,
      tracking,
      equipment: this.key(element, 'equipment', here, EQUIPMENT),
      primaryMuscles: this.muscles(element, 'primaryMuscles', here),
      secondaryMuscles: this.muscles(element, 'secondaryMuscles', here),
      note: this.text(element, 'note', here) ?? this.text(element, 'notes', here),
      loadStepKg: this.weight(element, 'loadStep', here),
      where: here,
    };
  }

  routine(element, here) {
    if (!isObject(element)) {
      this.invalid(here, element, 'each routine is an object with "name" and "exercises".');
      return null;
    }
    if (!this.single) this.fields(element, ROUTINE_FIELDS, here);
    const name = this.text(element, 'name', here, true);
    const exercises = this.list(element, 'exercises', here, true)
      .map((exercise, index) => this.exercise(exercise, here.in('exercises', index, exercise?.exercise ?? exercise?.name)))
      .filter(Boolean);
    const labels = new Map();
    exercises.forEach((exercise, index) => {
      if (exercise.superset === null) return;
      labels.set(exercise.superset, [...(labels.get(exercise.superset) ?? []), index]);
    });
    for (const [label, indices] of labels) {
      if (indices.at(-1) - indices[0] + 1 !== indices.length) {
        this.problems.warn(
          here,
          `Superset "${label}" isn't side by side, so each run becomes its own superset. Put a superset's exercises next to each other.`,
        );
      } else if (indices.length === 1) {
        this.problems.warn(here, `Superset "${label}" has one exercise; a superset needs two or more next to each other.`);
      }
    }
    return name ? { name, notes: this.text(element, 'notes', here), exercises, where: here } : null;
  }

  exercise(element, here) {
    if (!isObject(element)) {
      this.invalid(here, element, 'each exercise is an object with "exercise" and "sets".');
      return null;
    }
    const name = this.text(element, 'exercise', here) ?? this.text(element, 'name', here);
    if (!name) this.problems.error(here.in('exercise'), 'Missing: which exercise (its catalog name, a custom exercise\'s name, or an id).');
    const rest = this.number(element, 'rest', here, (text) => inRange(seconds(text), 0, 3600),
      'write seconds (90), a time ("2:30") or "90s"; 0 to 60 minutes, 0 turns the rest timer off.');
    this.fields(element, new Set([...EXERCISE_FIELDS, ...TARGET_FIELDS]), here);
    const sets = this.sets(element, here);
    let progression = this.text(element, 'progression', here)?.toLowerCase() ?? null;
    if (progression !== null && NO_PROGRESSION.has(progression)) progression = null;
    if (progression !== null && progression !== 'double') {
      this.invalid(here.in('progression'), progression, 'write "double" (double progression) or leave it out.');
      progression = null;
    }
    if (progression && sets.some((set) => set.type !== 'warmup' && set.type !== 'drop' && !set.reps?.range)) {
      this.problems.warn(
        here,
        'Double progression needs a rep range like "8-12" on every working set; without one, the set has nothing to climb through.',
      );
    }
    const superset = element.superset === undefined || element.superset === null ? null : String(element.superset).trim();
    if (!name) return null;
    return {
      exercise: name,
      superset,
      rest,
      progression,
      note: this.text(element, 'note', here) ?? this.text(element, 'notes', here),
      sets,
      where: here,
    };
  }

  sets(element, here) {
    const shared = this.targets(element, here);
    const sets = element.sets;
    if (sets === undefined || sets === null) {
      this.problems.error(here.in('sets'), 'Missing: a list of sets, or a number of sets alike with targets on the exercise.');
      return [];
    }
    if (!Array.isArray(sets) && typeof sets !== 'object') {
      const count = this.number(element, 'sets', here, count1to20, 'write a list of sets, or a whole number from 1 to 20.');
      return Array(count ?? 0).fill(shared);
    }
    if (!Array.isArray(sets)) {
      this.invalid(here.in('sets'), sets, 'write a list of sets, or a whole number from 1 to 20.');
      return [];
    }
    if (sets.length === 0) this.problems.error(here.in('sets'), 'Empty: give the exercise at least one set.');
    return sets.flatMap((set, index) => {
      const where = here.in('sets', index);
      if (!isObject(set)) {
        this.invalid(where, set, 'each set is an object such as { "reps": "8-12", "weight": 60 }.');
        return [];
      }
      this.fields(set, new Set([...TARGET_FIELDS, 'count']), where);
      const own = this.targets(set, where);
      // The set's own target where it gives one (or the exercise doesn't), else the exercise's.
      const pick = (key, field) => (field in set || !(field in element) ? own[key] : shared[key]);
      const planned = {
        type: pick('type', 'type'),
        weightKg: pick('weightKg', 'weight'),
        reps: pick('reps', 'reps'),
        durationSec: pick('durationSec', 'duration'),
        distanceM: pick('distanceM', 'distance'),
        rpe: pick('rpe', 'rpe'),
      };
      const count = this.number(set, 'count', where, count1to20, 'write a whole number from 1 to 20.') ?? 1;
      return Array(count).fill(planned);
    });
  }

  /** The targets in [fields]: a set's own, or those an exercise gives all its sets. */
  targets(fields, here) {
    const type = this.key(fields, 'type', here, SET_TYPES) ?? 'normal';
    return {
      type,
      weightKg: this.weight(fields, 'weight', here),
      reps: this.number(fields, 'reps', here, repRange,
        'write a number (8) or a range ("8-12"), 1 to 999. For as many as possible, use "type": "failure".'),
      durationSec: this.number(fields, 'duration', here, (text) => inRange(seconds(text), 1, 86400),
        'write seconds (45), a time ("1:30", "1:00:00") or with a unit ("90s", "2 min"); up to 24 hours.'),
      distanceM: this.number(fields, 'distance', here, (text) => inRange(meters(text), 0.1, 1_000_000),
        'write metres (400) or with a unit ("400 m", "5 km", "1 mi", "50 yd").'),
      rpe: this.number(fields, 'rpe', here, rpe, 'write 1 to 10 in half points, such as 8 or 7.5.'),
    };
  }

  weight(fields, name, here) {
    const text = this.text(fields, name, here);
    if (text === null) return null;
    const where = here.in(name);
    const parsed = quantity(text);
    const hint = 'write a number in the file\'s weightUnit, or with its unit: "80 kg", "175 lb" (up to 1000 kg).';
    if (!parsed) return this.invalid(where, text, hint);
    let unit;
    if (parsed.unit === null) {
      unit = this.weightUnit;
      if (!unit) {
        this.problems.error(where, `${text} has no unit: add "weightUnit": "kg" (or "lb") to the file, or write "${text} kg".`);
        return null;
      }
    } else {
      unit = weightUnit(parsed.unit);
      if (!unit) return this.invalid(where, text, hint);
    }
    const kg = unit === 'lb' ? parsed.value * KG_PER_LB : parsed.value;
    if (kg > MAX_KG) return this.invalid(where, text, hint);
    return kg > 0 ? kg : null;
  }

  number(fields, name, here, read, hint) {
    const text = this.text(fields, name, here);
    if (text === null) return null;
    const value = read(text);
    if (value === null || value === undefined) return this.invalid(here.in(name), text, hint);
    return value;
  }

  key(fields, name, here, allowed) {
    const text = this.text(fields, name, here);
    if (text === null) return null;
    const key = text.trim().toLowerCase();
    if (allowed.includes(key)) return key;
    return this.invalid(here.in(name), text, `use one of: ${allowed.join(', ')}.`);
  }

  muscles(fields, name, here) {
    const value = fields[name];
    if (value === undefined || value === null) return null;
    const list = Array.isArray(value) ? value : [value];
    return list
      .map((muscle) => {
        const key = typeof muscle === 'string' ? muscle.trim().toLowerCase() : null;
        if (key && MUSCLES.includes(key)) return key;
        return this.invalid(here.in(name), muscle, `use these keys: ${MUSCLES.join(', ')}.`);
      })
      .filter(Boolean);
  }

  text(fields, name, here, required = false) {
    const value = fields[name];
    let text = null;
    if (typeof value === 'string') text = value.trim() || null;
    else if (typeof value === 'number' || typeof value === 'boolean') text = String(value);
    else if (value !== undefined && value !== null) this.invalid(here.in(name), value, 'write text or a number here.');
    if (text === null && required && (value === undefined || value === null || typeof value === 'string')) {
      this.problems.error(here.in(name), 'Missing.');
    }
    return text;
  }

  list(fields, name, here, required = false) {
    const value = fields[name];
    if (Array.isArray(value)) {
      if (required && value.length === 0) this.problems.error(here.in(name), 'Empty: it needs at least one.');
      return value;
    }
    if (value === undefined || value === null) {
      if (required) this.problems.error(here.in(name), 'Missing.');
      return [];
    }
    this.invalid(here.in(name), value, 'write a list.');
    return [];
  }

  fields(object, known, here) {
    for (const field of Object.keys(object)) {
      if (!known.has(field)) {
        this.problems.warn(
          here,
          `Liora doesn't know "${field}" and leaves it out. Cues like tempo or form go in the exercise's "note"; the day's plan in the routine's "notes".`,
        );
      }
    }
  }

  invalid(where, value, hint) {
    const shown = typeof value === 'string' ? `"${value}"` : JSON.stringify(value);
    this.problems.error(where, `Liora can't read ${shown} here: ${hint}`);
    return null;
  }
}

/** What each exercise the plan names becomes, as Liora's import would suggest it. */
function resolve(plan, catalog, problems) {
  const custom = new Map(plan.customExercises.map((c) => [c.name.toLowerCase(), c]));
  const uses = new Map();
  for (const routine of plan.routines) {
    for (const exercise of routine.exercises) {
      const name = custom.get(exercise.exercise.toLowerCase())?.name ?? exercise.exercise;
      if (!uses.has(name)) uses.set(name, []);
      uses.get(name).push(exercise);
    }
  }
  for (const c of plan.customExercises) if (!uses.has(c.name)) uses.set(c.name, []);

  return [...uses].map(([name, planned]) => {
    const sets = planned.flatMap((p) => p.sets);
    const fields = {
      weight: sets.some((s) => s.weightKg),
      reps: sets.some((s) => s.reps),
      duration: sets.some((s) => s.durationSec),
      distance: sets.some((s) => s.distanceM),
    };
    const definition = custom.get(name.toLowerCase());
    const where = definition?.where ?? planned[0]?.where;
    let resolution;
    if (definition) {
      resolution = resolveCustom(definition, fields, catalog, problems);
    } else if (catalog.byId.has(name)) {
      const exercise = catalog.byId.get(name);
      resolution = { kind: exercise.custom ? 'yours' : 'catalog', exercise };
    } else if (/^(fedb|liora)\./.test(name)) {
      // An id that names nothing is a mistake, not a name for a new exercise.
      problems.error(where.in('exercise'), `No exercise has the id "${name}". Use \`search\` to find it, or its name.`);
      resolution = { kind: 'new', tracking: trackingFor(fields, null), basedOn: null };
    } else {
      const suggestion = catalog.suggestPlanned(name, fields);
      if (suggestion.existing) {
        resolution = { kind: suggestion.existing.custom ? 'yours' : 'catalog', exercise: suggestion.existing };
      } else {
        const base = suggestion.closest;
        resolution = { kind: 'new', tracking: trackingFor(fields, base?.tracking), basedOn: base };
        problems.warn(
          where,
          `"${name}" isn't in Liora's catalog, so Liora makes it a new exercise${base ? `, a variation of ${base.name}` : ''}. ` +
            `If you meant ${base ? `"${base.name}"` : 'a catalog exercise'}, use its name (search finds it); ` +
            "if it's really new, define it in \"customExercises\" with its tracking, equipment and muscles.",
        );
      }
    }
    const tracking = resolution.exercise?.tracking ?? resolution.tracking;
    for (const exercise of planned) {
      // As Liora saves it: targets without a column, and progression where it doesn't fit, are left out.
      exercise.tracking = tracking;
      const dropped = ['weight', 'reps', 'duration', 'distance'].filter(
        (field) => exercise.sets.some((s) => s[FIELD_KEYS[field]]) && !TRACKING[tracking]?.[field],
      );
      if (dropped.length) {
        problems.warn(
          exercise.where,
          `${display(resolution, name)} is tracked as ${tracking}, with no column for ${dropped.join(' or ')}: Liora leaves ${dropped.length > 1 ? 'them' : 'it'} out.`,
        );
      }
      if (exercise.progression && !PROGRESSING.has(tracking)) {
        exercise.progression = null;
        // Rep ranges don't matter for a rule that's left off.
        problems.warnings = problems.warnings.filter(
          (w) => !(w.where === exercise.where.text && w.message.startsWith('Double progression needs')),
        );
        problems.warn(
          exercise.where,
          `Double progression only works with weight × reps (${[...PROGRESSING].join(', ')}); ${display(resolution, name)} is ${tracking}, so Liora leaves it off.`,
        );
      }
    }
    return { name, sets: sets.length, fields, ...resolution };
  });
}

const FIELD_KEYS = { weight: 'weightKg', reps: 'reps', duration: 'durationSec', distance: 'distanceM' };

function resolveCustom(definition, fields, catalog, problems) {
  const base = definition.variationOf ? catalog.find(definition.variationOf) : null;
  if (definition.variationOf && !base) {
    problems.warn(
      definition.where.in('variationOf'),
      `No exercise "${definition.variationOf}" in the catalog, so this one stands on its own${definition.tracking ? '' : ` (tracked as ${trackingFor(fields, null)}, from its sets)`}. Use a catalog name (search finds it).`,
    );
  }
  const columns = definition.tracking ? TRACKING[definition.tracking] : fields;
  const same = catalog.suggest(definition.name, columns ?? {});
  if (same.existing) {
    problems.warn(
      definition.where,
      `${same.existing.custom ? 'The person already has' : 'Liora\'s catalog has'} "${same.existing.name}", the same exercise, so Liora uses it rather than making another. Name it directly instead of defining it.`,
    );
    return { kind: same.existing.custom ? 'yours' : 'catalog', exercise: same.existing };
  }
  return {
    kind: 'custom',
    tracking: definition.tracking ?? trackingFor(fields, base?.tracking),
    equipment: definition.equipment ?? base?.equipment ?? 'other',
    basedOn: base,
  };
}

const display = (resolution, name) => (resolution.exercise ? resolution.exercise.name : name);

function result(problems, plan, exercises) {
  return { ok: problems.errors.length === 0, errors: problems.errors, warnings: problems.warnings, plan, exercises };
}

/** The check as text for an AI to read: what's wrong, then what Liora will make of the file. */
export function report(check, { unit } = {}) {
  const lines = [];
  const { errors, warnings, plan, exercises } = check;
  if (errors.length) {
    lines.push(`✗ Liora can't import this yet: ${count(errors.length, 'error')}${warnings.length ? `, ${count(warnings.length, 'warning')}` : ''}.`);
  } else {
    const routines = plan.routines.length;
    const fresh = exercises.filter((e) => e.kind === 'new' || e.kind === 'custom').length;
    const where = plan.folder ? ` in a new ${plan.folder.rotation ? 'rotation' : 'folder'} "${plan.folder.name}"` : '';
    lines.push(
      `✓ Ready to import: ${count(routines, 'routine')}${where}${fresh ? `, ${count(fresh, 'new exercise')}` : ''}` +
        `${warnings.length ? ` (${count(warnings.length, 'warning')})` : ''}.`,
    );
  }
  if (errors.length) lines.push('', 'Errors (fix every one):', ...errors.map((e) => `  ${e.where}: ${e.message}`));
  if (warnings.length) lines.push('', 'Warnings:', ...warnings.map((w) => `  ${w.where}: ${w.message}`));
  if (plan && exercises.length) {
    const shownUnit = unit ?? plan.weightUnit ?? 'kg';
    const byName = new Map(exercises.map((e) => [e.name.toLowerCase(), e]));
    lines.push('', 'What Liora makes of it:');
    for (const routine of plan.routines) {
      lines.push(`  ${routine.name} (${count(routine.exercises.length, 'exercise')}, ${count(routine.exercises.reduce((n, e) => n + e.sets.length, 0), 'set')})`);
      routine.exercises.forEach((exercise, index) => {
        const resolved = byName.get(exercise.exercise.toLowerCase());
        const extras = [
          exercise.superset !== null ? `superset ${exercise.superset}` : null,
          exercise.rest !== null ? `rest ${clock(exercise.rest)}` : null,
          exercise.progression ? 'double progression' : null,
        ].filter(Boolean);
        lines.push(`    ${index + 1}. ${describe(resolved, exercise.exercise)}${extras.length ? ` · ${extras.join(' · ')}` : ''}`);
        lines.push(`       ${setsText(exercise.sets, shownUnit, exercise.tracking)}`);
      });
    }
    const own = exercises.filter((e) => e.kind === 'custom' && e.sets === 0);
    if (own.length) lines.push('  Exercises only:', ...own.map((e) => `    ${describe(e, e.name)}`));
  }
  return lines.join('\n');
}

function describe(resolved, name) {
  if (!resolved) return name;
  switch (resolved.kind) {
    case 'catalog':
      return `${resolved.exercise.name} [${resolved.exercise.id}]${sameName(resolved.exercise.name, name) ? '' : ` (from "${name}")`}`;
    case 'yours':
      return `${resolved.exercise.name} [the person's own, ${resolved.exercise.id}]`;
    case 'custom':
      return `${name} [new, ${resolved.tracking}${resolved.basedOn ? `, a variation of ${resolved.basedOn.name}` : ''}]`;
    default:
      return `${name} [NEW, not in the catalog${resolved.basedOn ? `; a variation of ${resolved.basedOn.name}` : ''}]`;
  }
}

const sameName = (a, b) => a.toLowerCase() === b.toLowerCase();

/** Sets in brief, as an exercise tracked as [tracking] keeps them: "2× warmup 40 kg × 10, 3× 80 kg × 6–8 @8". */
function setsText(sets, unit, tracking) {
  const columns = TRACKING[tracking] ?? { weight: true, reps: true, duration: true, distance: true };
  const groups = [];
  for (const set of sets) {
    const kept = {
      ...set,
      weightKg: columns.weight ? set.weightKg : null,
      reps: columns.reps ? set.reps : null,
      durationSec: columns.duration ? set.durationSec : null,
      distanceM: columns.distance ? set.distanceM : null,
      rpe: columns.reps ? set.rpe : null,
    };
    const text = setText(kept, unit);
    if (groups.at(-1)?.text === text) groups.at(-1).count++;
    else groups.push({ text, count: 1 });
  }
  return groups.map((g) => `${g.count > 1 ? `${g.count}× ` : ''}${g.text}`).join(', ') || 'no sets';
}

function setText(set, unit) {
  const parts = [];
  if (set.weightKg) parts.push(`${round(unit === 'lb' ? set.weightKg / KG_PER_LB : set.weightKg)} ${unit}`);
  if (set.reps) parts.push(set.reps.range ? `${set.reps.min}–${set.reps.max}` : `${set.reps.min}`);
  const distance = set.distanceM && (set.distanceM >= 1000 ? `${round(set.distanceM / 1000)} km` : `${round(set.distanceM)} m`);
  const time = set.durationSec && clock(set.durationSec);
  if (distance && time) parts.push(`${distance} in ${time}`);
  else if (distance || time) parts.push(distance || time);
  const body = parts.join(' × ') || 'open';
  return `${set.type === 'normal' ? '' : `${set.type} `}${body}${set.rpe ? ` @${set.rpe}` : ''}`;
}

const round = (value) => Math.round(value * 100) / 100;
const clock = (seconds) =>
  seconds >= 3600
    ? `${Math.floor(seconds / 3600)}:${pad(Math.floor(seconds / 60) % 60)}:${pad(seconds % 60)}`
    : `${Math.floor(seconds / 60)}:${pad(seconds % 60)}`;
const pad = (n) => String(n).padStart(2, '0');
const count = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;
const isObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

// Values in the forms people write them (RoutineFileValues.kt).

export function quantity(text) {
  const match = /^(\d+(?:[.,]\d+)?)\s*([a-zA-Z]*)\.?$/.exec(String(text).trim());
  if (!match) return null;
  return { value: Number(match[1].replace(',', '.')), unit: match[2].toLowerCase() || null };
}

export function weightUnit(name) {
  const key = String(name).trim().toLowerCase();
  if (['kg', 'kgs', 'kilo', 'kilos', 'kilogram', 'kilograms'].includes(key)) return 'kg';
  if (['lb', 'lbs', 'pound', 'pounds'].includes(key)) return 'lb';
  return null;
}

export function repRange(text) {
  const trimmed = String(text).trim();
  const ok = (n) => Number.isInteger(n) && n >= 1 && n <= 999;
  if (/^\d+$/.test(trimmed)) return ok(Number(trimmed)) ? { min: Number(trimmed), max: Number(trimmed), range: false } : null;
  const match = /^(\d+)\s*(?:-|–|—|to)\s*(\d+)$/i.exec(trimmed);
  if (!match) return null;
  const [min, max] = [Number(match[1]), Number(match[2])].sort((a, b) => a - b);
  return ok(min) && ok(max) ? { min, max, range: max > min } : null;
}

const SECONDS_PER = {
  s: 1, sec: 1, secs: 1, second: 1, seconds: 1, m: 60, min: 60, mins: 60, minute: 60, minutes: 60,
  h: 3600, hr: 3600, hrs: 3600, hour: 3600, hours: 3600,
};

export function seconds(text) {
  const trimmed = String(text).trim();
  const clockMatch = /^(\d+):(\d{2})(?::(\d{2}))?$/.exec(trimmed);
  if (clockMatch) {
    const parts = clockMatch.slice(1).filter((p) => p !== undefined).map(Number);
    if (parts.slice(1).some((p) => p >= 60)) return null;
    return parts.reduce((total, part) => total * 60 + part, 0);
  }
  const parsed = quantity(trimmed);
  const factor = parsed && SECONDS_PER[parsed.unit ?? 's'];
  return factor ? Math.round(parsed.value * factor) : null;
}

const METERS_PER = {
  m: 1, meter: 1, meters: 1, metre: 1, metres: 1, km: 1000, kilometer: 1000, kilometers: 1000, kilometre: 1000,
  kilometres: 1000, mi: 1609.344, mile: 1609.344, miles: 1609.344, yd: 0.9144, yard: 0.9144, yards: 0.9144,
};

export function meters(text) {
  const parsed = quantity(text);
  const factor = parsed && METERS_PER[parsed.unit ?? 'm'];
  return factor ? parsed.value * factor : null;
}

export function rpe(text) {
  const value = Number(String(text).trim().replace(',', '.'));
  return Number.isFinite(value) && value >= 1 && value <= 10 && Number.isInteger(value * 2) ? value : null;
}

const count1to20 = (text) => (/^\d+$/.test(String(text).trim()) ? inRange(Number(text), 1, 20) : null);
function inRange(value, min, max) {
  return value !== null && value !== undefined && value >= min && value <= max ? value : null;
}
