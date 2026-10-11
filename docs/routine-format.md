# Liora routine files

A routine file is a JSON document that Liora imports: one routine, or a whole program of them in a folder,
with any exercises it needs that aren't in Liora yet. AIs write them for people. Every choice a person makes
in Liora's routine editor fits in the file.

**For an AI:** reply with one JSON object in this format, and nothing that isn't in it. Save it as
`<name>.liora.json` if you can write files; otherwise put it in one code block. The person opens the file
with Liora, shares your answer to Liora, or copies it and taps Paste under Train › Import routines. Liora shows
what's in it before saving anything. If you can run the Liora routine tool (`tools/routine` in Liora's
repository, or its MCP server), use it to search the exercise catalog, to read the person's Liora backup for
their units, gyms and recent numbers, and to check the file before handing it over.

## A small one

```json
{
  "format": "liora-routine",
  "version": 1,
  "weightUnit": "kg",
  "routines": [
    {
      "name": "Full Body",
      "exercises": [
        { "exercise": "Barbell Squat", "sets": 3, "reps": "5-8", "weight": 80, "rest": "3:00", "progression": "double" },
        { "exercise": "Barbell Bench Press", "sets": 3, "reps": "6-10", "weight": 60, "progression": "double" },
        { "exercise": "Pull-Up", "sets": 3, "reps": "6-10" },
        { "exercise": "Plank", "sets": 2, "duration": "1:00" }
      ]
    }
  ]
}
```

## A program

```json
{
  "format": "liora-routine",
  "version": 1,
  "weightUnit": "lb",
  "folder": { "name": "Upper / Lower", "rotation": true },
  "customExercises": [
    {
      "name": "Cable Y-Raise",
      "variationOf": "Cable Lateral Raise",
      "primaryMuscles": ["shoulders"],
      "secondaryMuscles": ["traps"],
      "note": "Thumbs up, arms in a Y. Low cable, one side at a time."
    }
  ],
  "routines": [
    {
      "name": "Upper A",
      "notes": "Heavy day. Leave a rep in the tank on the first sets.",
      "exercises": [
        {
          "exercise": "Barbell Bench Press",
          "rest": "3:00",
          "progression": "double",
          "note": "Shoulder blades back and down.",
          "sets": [
            { "type": "warmup", "weight": 95, "reps": 10 },
            { "type": "warmup", "weight": 135, "reps": 5 },
            { "count": 3, "weight": 185, "reps": "6-8", "rpe": 8 }
          ]
        },
        { "exercise": "Chest-Supported Dumbbell Row", "superset": "A", "rest": 0, "sets": 3, "reps": "8-12" },
        { "exercise": "Cable Y-Raise", "superset": "A", "rest": 90, "sets": 3, "reps": "12-15" },
        {
          "exercise": "Triceps Pushdown",
          "sets": [
            { "count": 2, "weight": 50, "reps": "10-12" },
            { "type": "drop", "weight": 30, "reps": "12-15" }
          ]
        }
      ]
    },
    {
      "name": "Lower A",
      "exercises": [
        { "exercise": "Barbell Deadlift", "sets": 3, "reps": "4-6", "rest": "3:30" },
        { "exercise": "Farmer's Walk", "sets": 3, "weight": "70 lb", "distance": "40 m" },
        { "exercise": "Stationary Bike", "sets": [{ "duration": "10:00", "distance": "4 km" }] }
      ]
    }
  ]
}
```

## The file

| Field | Required | What it is |
|---|---|---|
| `format` | yes | Always `"liora-routine"`. |
| `version` | yes | `1`. A file for a newer version is refused, not misread. |
| `weightUnit` | when a weight is a bare number | `"kg"` or `"lb"`: what bare numbers in `weight` and `loadStep` mean. Liora shows weights in the person's own unit either way. |
| `folder` | no | Put every routine in a new folder: `{ "name": "Push Pull Legs", "rotation": true }`, or just its name. With `rotation`, the routines take turns in their order and the Train tab says which is next. Without a folder, routines go outside any folder. |
| `customExercises` | no | Exercises the routines use that Liora doesn't have. See below. |
| `routines` | yes | One or more routines, in the order they're listed in Liora (and taken in a rotation). A file that only brings custom exercises can leave it out. |

Fields Liora doesn't know are left out, with a note in the review, so a typo never passes unnoticed.

### A routine

| Field | Required | What it is |
|---|---|---|
| `name` | yes | Shown on the Train tab, e.g. `"Push A"`. |
| `notes` | no | The routine's notes: what the day is for, how to run it. |
| `exercises` | yes | In the order they're done. |

### An exercise in a routine

| Field | Required | What it is |
|---|---|---|
| `exercise` | yes | Which exercise: its name in Liora's catalog (English or German), a name from `customExercises`, or its id (`fedb.Barbell_Squat`, `liora.cable_lateral_raise`, or the id of someone's own exercise from their backup). |
| `sets` | yes | The planned sets: a list (below), or a number of sets alike, with their targets on the exercise itself (`"sets": 3, "reps": "8-12", "weight": 60`). Targets on the exercise also apply to each set in a list that doesn't give its own. |
| `rest` | no | Rest after each of its sets (a superset: after each round). Seconds, or a time like `"2:30"` or `"90s"`. `0` turns the rest timer off. Left out, the person's own rest settings apply. |
| `superset` | no | A label such as `"A"`. Exercises next to each other with the same label are a superset: one set of each in turn, rest after the round. Give the last exercise of a superset the rest, and the others `0`. |
| `progression` | no | `"double"` for double progression: each working set has a rep range, and once every one reaches the top, the next workout goes up a weight step and starts again at the bottom. Only for exercises counted in weight and reps (`weight_reps`, `weighted_bodyweight`), and every working set needs a range like `"8-12"`. |
| `note` | no | The exercise's own note, shown wherever it appears (seat height, grip, cues). Liora keeps a note the person already has on that exercise rather than replacing it. |

### A set

| Field | What it is |
|---|---|
| `type` | `"normal"` (the default), `"warmup"`, `"drop"` or `"failure"`. Warm-ups and drop sets don't count for progression or records. |
| `count` | How many sets alike, 1–20. Defaults to 1. |
| `weight` | The target weight: a number in `weightUnit`, or with its unit, `"80 kg"` or `"175 lb"`. For `weighted_bodyweight` it's the added load; for `assisted_bodyweight`, the assistance. Leave it out when you don't know what the person lifts: Liora fills in what they did last time. |
| `reps` | A number (`8`) or a range (`"8-12"`). |
| `duration` | Seconds, or a time: `"45s"`, `"1:30"`, `"2 min"`, `"1:00:00"`. |
| `distance` | Metres, or with a unit: `"400 m"`, `"5 km"`, `"1 mi"`, `"50 yd"`. |
| `rpe` | Target RPE, 1–10 in half points. |

Give a set the targets its exercise is tracked by: weight and reps for a barbell lift, reps for a pull-up,
duration for a plank, distance and duration for a run, weight and distance for a carry. A target the
exercise has no column for is left out, with a note. A set with no targets at all is fine: an empty set to
fill in on the day.

### A custom exercise

Only for what Liora doesn't have: a machine particular to a gym, a variation, a drill. Search the catalog
first (the routine tool does it); two exercises for the same movement split the person's history in two.

| Field | Required | What it is |
|---|---|---|
| `name` | yes | Its name. Routines refer to it by this name. |
| `variationOf` | no | The catalog exercise it's a variation of (name or id). It starts with that one's tracking, equipment and muscles, so only give what differs. |
| `tracking` | without `variationOf` | How a set is measured (keys below). |
| `equipment` | no | Key below. Defaults to the base exercise's, else `other`. |
| `primaryMuscles` | no | Keys below. Count toward the person's sets per muscle. |
| `secondaryMuscles` | no | Keys below. |
| `note` | no | Its own note, as for an exercise in a routine. |
| `loadStep` | no | For a machine or cable whose stack doesn't move in the gym's usual step: what one step adds, in `weightUnit` (or with its unit). |

If the person already has an exercise of that name, Liora uses theirs instead of making a second one.

### Keys

- `tracking`: `weight_reps` (weight × reps), `bodyweight_reps` (reps), `weighted_bodyweight` (added weight ×
  reps: weighted pull-ups, dips), `assisted_bodyweight` (assistance × reps), `duration`, `duration_weight`
  (weighted holds), `distance_duration` (runs, rides, rows), `weight_distance` (carries, sled pushes).
- `equipment`: `barbell`, `dumbbell`, `kettlebell`, `machine`, `cable`, `ez_bar`, `band`, `bodyweight`,
  `medicine_ball`, `exercise_ball`, `foam_roll`, `other`.
- Muscles: `chest`, `shoulders`, `triceps`, `biceps`, `forearms`, `lats`, `middle_back`, `lower_back`,
  `traps`, `neck`, `abdominals`, `quadriceps`, `hamstrings`, `glutes`, `adductors`, `abductors`, `calves`.

## How names are matched

Liora's catalog has about 800 exercises, each with English and German names and common aliases. A name is
the same exercise when it has the same words as one of those names, in any order, give or take small words
and equipment words that agree ("Bench Press (Barbell)" is "Barbell Bench Press"). Weight on a bodyweight
exercise picks its weighted variant ("Pull-Up" with a weight is "Weighted Pull Ups").

A name that isn't the same as any exercise becomes a new exercise, a variation of the closest one, and the
review lists it under "New exercises", where the person can pick another. So use catalog names: the routine
tool's `search` finds them, and its `check` says which names Liora would make new.

## Writing a good routine

- Leave out weights you don't know. Liora fills in each set from the person's last time, and a routine with
  wrong weights is worse than one without.
- Give working sets rep ranges (`"8-12"`) when using double progression, and starting weights the person
  can do for the bottom of the range.
- Warm-ups are sets of type `warmup` before the working sets. Liora can also add them on the day.
- Name routines for what they are ("Push", "Upper A", "Day 1 — Squat"), and keep the plan's reasoning in
  the routine's `notes` and the exercises' `note`s, briefly: they show on the phone mid-workout.
- Several routines that are done in turn belong in a folder with `"rotation": true`.

## Versions

This is version 1. Later versions may add fields; a file that uses only these keeps importing.
