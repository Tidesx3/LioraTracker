// Liora's exercise catalog, and how Liora matches the names in a routine file to it. The matching is a port
// of the app's ExerciseMatcher (core/domain/.../importing/ExerciseMatcher.kt) and SearchText: keep the two in
// step, so what `check` says is what Liora does.

import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const CATALOG_PATH = join(repoRoot, 'app', 'android', 'src', 'main', 'assets', 'seed', 'exercises.json');
const RAW = 'https://raw.githubusercontent.com/Tidesx3/LioraTracker/main/';

/**
 * A file from Liora's repository: from the checkout this tool is in, or, for a copy of the tool on its own,
 * from GitHub (kept in the temp folder for a day).
 */
export async function repoFile(path) {
  const local = join(repoRoot, ...path.split('/'));
  if (existsSync(local)) return readFileSync(local, 'utf8');
  const cached = join(tmpdir(), 'liora-routine', path.replaceAll('/', '_'));
  if (existsSync(cached) && Date.now() - statSync(cached).mtimeMs < DAY_MS) return readFileSync(cached, 'utf8');
  const response = await fetch(RAW + path);
  if (!response.ok) throw new Error(`Couldn't fetch ${path} from GitHub: ${response.status}`);
  const text = await response.text();
  mkdirSync(dirname(cached), { recursive: true });
  writeFileSync(cached, text);
  return text;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** The catalog: built-in exercises, plus a person's own from their backup when given. */
export async function loadCatalog({ catalogPath = process.env.LIORA_CATALOG, own = [] } = {}) {
  const text = catalogPath
    ? readFileSync(catalogPath, 'utf8')
    : existsSync(CATALOG_PATH)
      ? readFileSync(CATALOG_PATH, 'utf8')
      : await repoFile('app/android/src/main/assets/seed/exercises.json');
  const builtIn = JSON.parse(text).exercises.map((e) => ({
    id: e.id,
    name: e.names.en,
    names: e.names,
    aliases: [...(e.aliases?.en ?? []), ...(e.aliases?.de ?? [])],
    tracking: e.tracking,
    equipment: e.equipment,
    category: e.category,
    primary: e.primary ?? [],
    secondary: e.secondary ?? [],
    instructions: e.instructions ?? [],
    rank: e.rank ?? null,
    custom: false,
  }));
  return new Catalog([...builtIn, ...own]);
}

export const TRACKING = {
  weight_reps: { weight: true, reps: true },
  bodyweight_reps: { reps: true },
  weighted_bodyweight: { weight: true, reps: true },
  assisted_bodyweight: { weight: true, reps: true },
  duration: { duration: true },
  duration_weight: { weight: true, duration: true },
  distance_duration: { duration: true, distance: true },
  weight_distance: { weight: true, distance: true },
};
export const EQUIPMENT = [
  'barbell', 'dumbbell', 'kettlebell', 'machine', 'cable', 'ez_bar', 'band', 'bodyweight', 'medicine_ball',
  'exercise_ball', 'foam_roll', 'other',
];
export const MUSCLES = [
  'chest', 'shoulders', 'triceps', 'biceps', 'forearms', 'lats', 'middle_back', 'lower_back', 'traps', 'neck',
  'abdominals', 'quadriceps', 'hamstrings', 'glutes', 'adductors', 'abductors', 'calves',
];
/** Exercises double progression fits: counted in weight and reps (ProgressionRule.fits). */
export const PROGRESSING = new Set(['weight_reps', 'weighted_bodyweight']);

/** Whether an exercise tracked as [tracking] has a column for every value in [fields]. */
export const fits = (fields, tracking) => {
  const columns = TRACKING[tracking] ?? {};
  return ['weight', 'reps', 'duration', 'distance'].every((field) => !fields[field] || columns[field]);
};

/** The tracking type for a new exercise based on one tracked as [base] (SetFields.trackingTypeFor). */
export function trackingFor(fields, base) {
  if (base && fits(fields, base)) return base;
  if (base === 'bodyweight_reps' && fits(fields, 'weighted_bodyweight')) return 'weighted_bodyweight';
  if (fields.distance && fields.weight) return 'weight_distance';
  if (fields.distance) return 'distance_duration';
  if (fields.duration && fields.weight && !fields.reps) return 'duration_weight';
  if (fields.duration && !fields.reps) return 'duration';
  if (fields.weight) return 'weight_reps';
  if (fields.reps) return 'bodyweight_reps';
  return base ?? 'weight_reps';
}

export class Catalog {
  constructor(exercises) {
    this.exercises = exercises;
    this.byId = new Map(exercises.map((e) => [e.id, e]));
    this.terms = exercises.flatMap((exercise) =>
      [exercise.name, ...Object.values(exercise.names ?? {}), ...(exercise.aliases ?? [])]
        .filter((name, index, all) => name && all.indexOf(name) === index)
        .map((name, index) => ({ exercise, words: matchWords(name), isName: index === 0 }))
        .filter((term) => term.words.size > 0),
    );
    this.search = this.search.bind(this);
  }

  /** Exercises for [query] (any language, any word order, typos forgiven), common ones first. */
  search(query, { equipment, muscle, tracking, limit = 15 } = {}) {
    const wanted = new SearchText(query ?? '');
    const scored = this.exercises
      .filter((e) => !equipment || e.equipment === equipment)
      .filter((e) => !muscle || e.primary.includes(muscle) || e.secondary.includes(muscle))
      .filter((e) => !tracking || e.tracking === tracking)
      .map((exercise) => {
        if (wanted.tokens.length === 0) return { exercise, score: 1 };
        const display = score(wanted, new SearchText(exercise.name));
        const others = [...Object.values(exercise.names ?? {}), ...(exercise.aliases ?? [])].map(
          (name) => score(wanted, new SearchText(name)),
        );
        return { exercise, score: Math.max(display > 0 ? display + 5 : 0, ...others) };
      })
      .filter((hit) => hit.score > 0);
    // Unlike the app's search, which ranks as you type, every word found is one tier, ordered by how common
    // the exercise is: an AI asking for "bench" means the bench press, not every name starting with "bench".
    const tier = (hit) => (hit.score >= 80 ? 3 : hit.score >= 60 ? 2 : 1);
    scored.sort(
      (a, b) =>
        tier(b) - tier(a) ||
        (a.exercise.rank ?? Infinity) - (b.exercise.rank ?? Infinity) ||
        b.score - a.score ||
        a.exercise.name.length - b.exercise.name.length ||
        a.exercise.name.localeCompare(b.exercise.name),
    );
    return scored.slice(0, limit).map((hit) => hit.exercise);
  }

  /** The exercise [reference] names: by id, or by the same words as one of its names. */
  find(reference) {
    return this.byId.get(reference) ?? (this.suggest(reference, {}).existing ?? null);
  }

  /**
   * What Liora makes of a name in a routine file whose sets hold [fields] (ExerciseMatcher.suggest):
   * `{ existing }` for an exercise it has, else `{ closest }` for a new one based on the closest, if any.
   */
  suggest(name, fields) {
    const words = matchWords(name);
    const same = this.terms.filter((term) => isSame(words, term)).sort(bySameness(words))[0];
    if (same) {
      const exercise = same.exercise;
      const existing = fits(fields, exercise.tracking) ? exercise : this.variantOf(same, fields);
      return existing ? { existing, closest: existing } : { closest: exercise };
    }
    return { closest: this.closest(words) };
  }

  /** As Liora reads a planned exercise: one of the same name is still meant when its columns don't fit. */
  suggestPlanned(name, fields) {
    const suggestion = this.suggest(name, fields);
    if (suggestion.existing) return suggestion;
    const same = this.suggest(name, {});
    return same.existing ? same : suggestion;
  }

  variantOf(same, fields) {
    const extra = (term) => [...term.words].filter((word) => !same.words.has(word));
    return (
      this.terms
        .filter((term) => term.exercise.id !== same.exercise.id && fits(fields, term.exercise.tracking))
        .filter((term) => [...same.words].every((word) => term.words.has(word)))
        .sort(
          (a, b) =>
            Number(!extra(a).every((w) => WEIGHTED.has(w))) - Number(!extra(b).every((w) => WEIGHTED.has(w))) ||
            extra(a).length - extra(b).length ||
            (a.exercise.rank ?? Infinity) - (b.exercise.rank ?? Infinity) ||
            a.exercise.name.localeCompare(b.exercise.name),
        )[0]?.exercise ?? null
    );
  }

  closest(words) {
    let best = null;
    for (const term of this.terms) {
      if (term.exercise.custom) continue;
      const shared = [...words].filter((word) => [...term.words].some((other) => alike(word, other)));
      const all = words.size + term.words.size;
      if (!shared.some((word) => !WEAK.has(word)) || 3 * 2 * shared.length < all) continue;
      const candidate = {
        exercise: term.exercise,
        dice: (2 * shared.length) / all,
        covered: shared.length / term.words.size,
        isName: term.isName,
        rank: term.exercise.rank ?? Infinity,
      };
      if (!best || better(candidate, best)) best = candidate;
    }
    return best?.exercise ?? null;
  }
}

const better = (a, b) =>
  a.dice !== b.dice
    ? a.dice > b.dice
    : a.covered !== b.covered
      ? a.covered > b.covered
      : a.isName !== b.isName
        ? a.isName
        : a.rank < b.rank;

function isSame(words, term) {
  if (![...words].some((word) => term.words.has(word) && !WEAK.has(word))) return false;
  return differing(words, term.words).every(
    (word) => SMALL.has(word) || EQUIPMENT_WORDS[word] === term.exercise.equipment,
  );
}

const bySameness = (words) => (a, b) =>
  differing(words, a.words).length - differing(words, b.words).length ||
  Number(!a.isName) - Number(!b.isName) ||
  (a.exercise.rank ?? Infinity) - (b.exercise.rank ?? Infinity) ||
  a.exercise.name.localeCompare(b.exercise.name);

const differing = (a, b) => [...[...a].filter((w) => !b.has(w)), ...[...b].filter((w) => !a.has(w))];

const alike = (a, b) =>
  a === b || (Math.min(a.length, b.length) >= 5 && Math.abs(a.length - b.length) <= 2 && (a.startsWith(b) || b.startsWith(a)));

const singular = (word) => {
  if (word.length <= 2) return word;
  if (word.endsWith('ies')) return `${word.slice(0, -3)}y`;
  if (word.endsWith('yes')) return word.slice(0, -2);
  if (word.endsWith('s') && !word.endsWith('ss')) return word.slice(0, -1);
  return word;
};

/** A name's words as matching compares them. */
export const matchWords = (name) => new Set(new SearchText(name).tokens.map(singular));
const words = (...list) => new Set(list.map(singular));

const SMALL = words(
  'a', 'am', 'an', 'and', 'at', 'auf', 'das', 'de', 'dem', 'den', 'der', 'des', 'die', 'for', 'im', 'in', 'mit',
  'of', 'on', 'the', 'to', 'und', 'with',
);
const EQUIPMENT_WORDS = {
  machine: 'machine', maschine: 'machine', cable: 'cable', kabel: 'cable', kabelzug: 'cable',
  barbell: 'barbell', langhantel: 'barbell', dumbbell: 'dumbbell', kurzhantel: 'dumbbell',
  kettlebell: 'kettlebell', kugelhantel: 'kettlebell', band: 'band', bodyweight: 'bodyweight',
  korpergewicht: 'bodyweight',
};
const WEIGHTED = words('weighted', 'gewichtet');
const WEAK = new Set([
  ...SMALL, ...Object.keys(EQUIPMENT_WORDS), ...WEIGHTED,
  ...words('seated', 'sitzend', 'sitzende', 'sitzendes', 'standing', 'stehend', 'grip', 'griff'),
]);

// Search, as the app's ExerciseSearch: case, umlauts and their spelled-out forms, word order and typos
// don't matter.

const FOLDING = {
  ä: 'a', ö: 'o', ü: 'u', ß: 'ss', à: 'a', á: 'a', â: 'a', é: 'e', è: 'e', ê: 'e', í: 'i', ó: 'o', ô: 'o',
  ú: 'u', ñ: 'n', ç: 'c', '°': '',
};

class SearchText {
  constructor(raw) {
    this.normalized = [...raw.toLowerCase()]
      .map((char) => FOLDING[char] ?? char)
      .join('')
      .replaceAll('ae', 'a')
      .replaceAll('oe', 'o')
      .replaceAll('ue', 'u')
      .replace(/[^\p{L}\p{N}]/gu, ' ')
      .trim()
      .replace(/\s+/g, ' ');
    this.tokens = this.normalized.split(' ').filter(Boolean);
    this.compact = this.tokens.join('');
  }
}

function score(query, candidate) {
  if (candidate.normalized === query.normalized) return 100;
  if (candidate.normalized.startsWith(query.normalized)) return 90;
  if (query.tokens.every((q) => candidate.tokens.some((t) => t.startsWith(q)))) return 80;
  if (candidate.compact.includes(query.compact)) return 60;
  if (query.tokens.every((q) => candidate.tokens.some((t) => fuzzy(q, t)))) return 40;
  return 0;
}

function fuzzy(query, token) {
  if (token.startsWith(query)) return true;
  const allowed = query.length >= 8 ? 2 : query.length >= 4 ? 1 : -1;
  if (allowed < 0) return false;
  return distance(query, token) <= allowed || distance(query, token.slice(0, query.length)) <= allowed;
}

/** Optimal string alignment distance: insert, delete, substitute, swap neighbours. */
function distance(a, b) {
  const d = Array.from({ length: a.length + 1 }, (_, i) => [i, ...Array(b.length).fill(0)]);
  for (let j = 1; j <= b.length; j++) d[0][j] = j;
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
    }
  }
  return d[a.length][b.length];
}
