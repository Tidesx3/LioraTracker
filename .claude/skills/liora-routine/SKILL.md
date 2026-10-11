---
name: liora-routine
description: Designs workout routines and programs (splits, rotations, supersets, progression, custom exercises) as a file the person imports into Liora, the workout tracker in this repo. Use when someone asks for a routine, workout plan, training program or split for Liora, wants one changed, or wants their Liora history turned into a new plan.
argument-hint: "[what you'd like, e.g. 3-day full body, 45 minutes, home gym]"
allowed-tools: Bash(node tools/routine/liora-routine.mjs *) Bash(node ${CLAUDE_PROJECT_DIR}/tools/routine/liora-routine.mjs *)
---

# Make a Liora routine

You write a routine file (`<name>.liora.json`, format `liora-routine`) that the person imports on their phone.
Liora shows it before saving anything: what the file holds and which exercise each name becomes. Everything a
person can do in Liora's routine editor fits in the file: routines in a folder or rotation, exercises in order,
supersets, rest, warm-up, drop and failure sets, weights, rep ranges, times, distances, RPE, double progression,
notes, and new exercises of their own (variations of catalog exercises, or new ones).

The tool is `node tools/routine/liora-routine.mjs` from the repo root (`${CLAUDE_PROJECT_DIR}`). If it isn't
there but `liora_*` MCP tools are (the same tool as an MCP server), use those instead: `liora_routine_format`,
`liora_search_exercises`, `liora_exercise_details`, `liora_check_routine`, `liora_save_routine`, `liora_read_backup`.

What they asked for: $ARGUMENTS

## 1. Know the person

You need: the goal (strength, muscle, fitness, a sport), days a week, time per session, experience, equipment
(gym, home, what's there), injuries or movements to avoid, and kilograms or pounds. Ask only what's missing and
matters, all at once (AskUserQuestion suits a few choices); don't interrogate. Sensible defaults beat questions.

If they use Liora already, ask once whether they'd like to share a backup (Settings › Your data › Back up
everything, a .zip). With one:

```sh
node tools/routine/liora-routine.mjs profile <backup.zip>
```

It gives their unit, gyms (bars, plates, dumbbells), their own exercises, their current routines and recent
training with estimated 1RMs. Plan from it: keep exercises they do, use their own exercises by name, and set
weights from their numbers, rounded to what their gym can load. Pass it to `check` too (`--backup`), so names
match their exercises. Without numbers, leave weights out: Liora fills each set in from last time.

## 2. Read the format

```sh
node tools/routine/liora-routine.mjs format
```

Read it once, fully (docs/routine-format.md). It's short and has examples.

## 3. Choose exercises from the catalog

```sh
node tools/routine/liora-routine.mjs search incline dumbbell press
node tools/routine/liora-routine.mjs search --muscle glutes --equipment machine
node tools/routine/liora-routine.mjs show "Romanian Deadlift"
```

Use catalog names exactly as `search` prints them (or the ids in brackets); common exercises come first, and
they're the ones people's gyms have. A name Liora doesn't know becomes a new exercise, which splits the
person's history, so only define `customExercises` for what the catalog really lacks: a gym's own machine, a
variation (`variationOf` a catalog exercise), a drill.

## 4. Write the plan

Program it like a good coach would, plainly:

- Big compound lifts first, while fresh; isolation after. Balance pushing and pulling, and legs.
- For muscle, roughly 10–20 hard sets per muscle a week, spread over the days; for strength, fewer reps and more
  rest on the main lifts.
- Rest about 2–3 minutes on heavy compounds, 60–90 seconds on isolation. Superset unrelated muscles (or
  antagonists) to save time: the first exercise gets `"rest": 0`, the last the rest for the round.
- With `"progression": "double"`, give every working set a range (`"8-12"`) and a starting weight the person
  can do for the bottom of it. Warm-ups (`"type": "warmup"`) before the first heavy lift.
- A split done in turn (upper/lower, push/pull/legs) is a folder with `"rotation": true`, routines in order.
- Put the why in a sentence or two: the routine's `notes` (the day's aim) and an exercise's `note` (a cue).
  They show mid-workout, so keep them short.

Write it to `<name>.liora.json` in the working directory, or where the person asks.

## 5. Check it

```sh
node tools/routine/liora-routine.mjs check <name>.liora.json [--backup <backup.zip>]
```

Fix every error and check again until it says "Ready to import". Read the warnings: a name that becomes a new
exercise usually means a catalog name was meant. "What Liora makes of it" is exactly what they'll get; read it
against what you meant.

## 6. Hand it over

Tell them briefly what the plan is (a small table of days and exercises works), where the file is, and how to
import it: get the file onto the phone (Quick Share, Google Drive, email, a cable) and open it with Liora, or in
Liora go to Train › the import button › Open file. Pasting the JSON there works too, so a short file can travel
by any messenger.

To change a routine later, write a new file: an import always adds routines, and the person deletes the old
one in Liora when they're happy with the new.
