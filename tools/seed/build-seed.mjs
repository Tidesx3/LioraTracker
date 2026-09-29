#!/usr/bin/env node
// Builds the bundled exercise catalog from free-exercise-db (public domain, Unlicense) plus Liora's
// curation: English renames, German names, search aliases, tracking types, ranks and extra exercises.
//
//   node tools/seed/build-seed.mjs
//
// Bump SEED_VERSION whenever the output changes; the app re-seeds built-ins when it sees a newer version.

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const SEED_VERSION = 1;
const FEDB_COMMIT = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5";
const FEDB_URL = `https://raw.githubusercontent.com/yuhonas/free-exercise-db/${FEDB_COMMIT}/dist/exercises.json`;

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..", "..");
const output = join(root, "app", "android", "src", "main", "assets", "seed", "exercises.json");
const cacheFile = join(here, ".cache", `fedb-${FEDB_COMMIT}.json`);

const TRACKING = new Set([
  "weight_reps", "bodyweight_reps", "weighted_bodyweight", "assisted_bodyweight",
  "duration", "duration_weight", "distance_duration", "weight_distance",
]);
const MUSCLES = new Set([
  "chest", "shoulders", "triceps", "biceps", "forearms", "lats", "middle_back", "lower_back", "traps", "neck",
  "abdominals", "quadriceps", "hamstrings", "glutes", "adductors", "abductors", "calves",
]);
const EQUIPMENT_MAP = {
  "body only": "bodyweight", machine: "machine", other: "other", "foam roll": "foam_roll",
  kettlebells: "kettlebell", dumbbell: "dumbbell", cable: "cable", barbell: "barbell", bands: "band",
  "medicine ball": "medicine_ball", "exercise ball": "exercise_ball", "e-z curl bar": "ez_bar",
};
const EQUIPMENT = new Set(Object.values(EQUIPMENT_MAP));
const CATEGORY_MAP = {
  strength: "strength", powerlifting: "powerlifting", "olympic weightlifting": "olympic_weightlifting",
  strongman: "strongman", plyometrics: "plyometrics", cardio: "cardio",
};

const errors = [];
const fail = (message) => errors.push(message);

async function loadFedb() {
  try {
    return JSON.parse(await readFile(cacheFile, "utf8"));
  } catch {
    const response = await fetch(FEDB_URL);
    if (!response.ok) throw new Error(`Download failed: ${response.status} ${FEDB_URL}`);
    const text = await response.text();
    await mkdir(dirname(cacheFile), { recursive: true });
    await writeFile(cacheFile, text);
    return JSON.parse(text);
  }
}

const readJson = async (name) => JSON.parse(await readFile(join(here, name), "utf8"));

const fedb = (await loadFedb()).filter((e) => e.category !== "stretching");
const overrides = await readJson("overrides.json");
const german = { ...(await readJson("de-a.json")), ...(await readJson("de-b.json")) };

const byName = new Map(fedb.map((e) => [e.name, e]));
const extraNames = new Set(overrides.extras.map((e) => e.en));
const knownName = (name) => byName.has(name) || extraNames.has(name);

// Every curated reference must point at a real exercise, so typos fail loudly.
for (const name of Object.keys(overrides.renameEn)) if (!byName.has(name)) fail(`renameEn: unknown "${name}"`);
for (const name of Object.keys(overrides.aliases)) if (!byName.has(name)) fail(`aliases: unknown "${name}"`);
for (const name of Object.keys(overrides.equipment)) if (!byName.has(name)) fail(`equipment: unknown "${name}"`);
for (const [type, names] of Object.entries(overrides.tracking)) {
  if (!TRACKING.has(type)) fail(`tracking: unknown type "${type}"`);
  for (const name of names) if (!byName.has(name)) fail(`tracking.${type}: unknown "${name}"`);
}
for (const name of overrides.rank) if (!knownName(name)) fail(`rank: unknown "${name}"`);
if (new Set(overrides.rank).size !== overrides.rank.length) fail("rank: duplicate entries");
for (const name of Object.keys(german)) if (!byName.has(name)) fail(`de: unknown "${name}"`);

const trackingByName = new Map();
for (const [type, names] of Object.entries(overrides.tracking)) {
  for (const name of names) {
    if (trackingByName.has(name)) fail(`tracking: "${name}" listed twice`);
    trackingByName.set(name, type);
  }
}
const rankOf = (name) => {
  const index = overrides.rank.indexOf(name);
  return index === -1 ? null : index + 1;
};

function equipmentFor(e, category) {
  if (overrides.equipment[e.name]) return overrides.equipment[e.name];
  if (e.equipment == null) return category === "cardio" ? "other" : "bodyweight";
  return EQUIPMENT_MAP[e.equipment] ?? fail(`equipment: unmapped "${e.equipment}" on "${e.name}"`);
}

function trackingFor(e, category, equipment) {
  if (trackingByName.has(e.name)) return trackingByName.get(e.name);
  if (category === "cardio") return "distance_duration";
  if (equipment === "bodyweight") return "bodyweight_reps";
  if (category === "plyometrics" && equipment === "other") return "bodyweight_reps";
  return "weight_reps";
}

const unique = (list) => [...new Set(list.filter(Boolean))];

const exercises = fedb.map((e) => {
  const category = CATEGORY_MAP[e.category] ?? fail(`category: unmapped "${e.category}"`);
  const equipment = equipmentFor(e, category);
  const nameEn = overrides.renameEn[e.name] ?? e.name;
  const nameDe = german[e.name];
  if (!nameDe) fail(`de: missing translation for "${e.name}"`);
  const aliases = overrides.aliases[e.name] ?? { en: [], de: [] };
  return {
    id: `fedb.${e.id}`,
    names: { en: nameEn, de: nameDe },
    aliases: {
      // A renamed exercise stays findable under its original dataset name.
      en: unique([...(aliases.en ?? []), nameEn !== e.name ? e.name : null]),
      de: unique(aliases.de ?? []),
    },
    tracking: trackingFor(e, category, equipment),
    equipment,
    category,
    primary: e.primaryMuscles.map((m) => m.replace(" ", "_")),
    secondary: e.secondaryMuscles.map((m) => m.replace(" ", "_")),
    instructions: e.instructions,
    images: e.images,
    rank: rankOf(e.name),
  };
});

for (const x of overrides.extras) {
  exercises.push({
    id: x.id,
    names: { en: x.en, de: x.de },
    aliases: { en: unique(x.aliases?.en ?? []), de: unique(x.aliases?.de ?? []) },
    tracking: x.tracking,
    equipment: x.equipment,
    category: x.category,
    primary: x.primary,
    secondary: x.secondary,
    instructions: x.instructions,
    images: [],
    rank: rankOf(x.en),
  });
}

// Structural validation of the final catalog.
const ids = new Set();
const namesDe = new Map();
for (const x of exercises) {
  if (ids.has(x.id)) fail(`duplicate id ${x.id}`);
  ids.add(x.id);
  if (!TRACKING.has(x.tracking)) fail(`${x.id}: bad tracking "${x.tracking}"`);
  if (!EQUIPMENT.has(x.equipment)) fail(`${x.id}: bad equipment "${x.equipment}"`);
  if (!Object.values(CATEGORY_MAP).includes(x.category)) fail(`${x.id}: bad category "${x.category}"`);
  for (const m of [...x.primary, ...x.secondary]) if (!MUSCLES.has(m)) fail(`${x.id}: bad muscle "${m}"`);
  if (x.primary.length === 0) fail(`${x.id}: no primary muscle`);
  if (namesDe.has(x.names.de)) fail(`duplicate German name "${x.names.de}" (${namesDe.get(x.names.de)}, ${x.id})`);
  namesDe.set(x.names.de, x.id);
}

if (errors.length > 0) {
  console.error(`Seed validation failed (${errors.length}):\n  ${errors.join("\n  ")}`);
  process.exit(1);
}

exercises.sort((a, b) => a.id.localeCompare(b.id));
const body = exercises.map((x) => "    " + JSON.stringify(x)).join(",\n");
const json =
  `{\n  "version": ${SEED_VERSION},\n` +
  `  "source": "free-exercise-db@${FEDB_COMMIT.slice(0, 7)} (Unlicense) + Liora curation",\n` +
  `  "exercises": [\n${body}\n  ]\n}\n`;
await mkdir(dirname(output), { recursive: true });
await writeFile(output, json);

const byTracking = {};
for (const x of exercises) byTracking[x.tracking] = (byTracking[x.tracking] ?? 0) + 1;
console.log(`Wrote ${exercises.length} exercises (seed v${SEED_VERSION}) to ${output}`);
console.log(`Tracking types: ${JSON.stringify(byTracking)}`);
console.log(`Ranked: ${exercises.filter((x) => x.rank).length}`);
