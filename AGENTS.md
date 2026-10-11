# Notes for AI agents

**Working on Liora's code:** read [CLAUDE.md](CLAUDE.md). It holds the commands, architecture rules and
conventions for every agent, whatever its name.

**Making a workout routine for someone to import into Liora:** don't change the code. Write a routine file as
described in [docs/routine-format.md](docs/routine-format.md), with the tool in
[tools/routine](tools/routine/README.md):

1. `node tools/routine/liora-routine.mjs format`: read the format once.
2. Ask what's missing: goal, days a week, session length, experience, equipment, injuries, kg or lb. If they
   share a Liora backup, `node tools/routine/liora-routine.mjs profile <backup.zip>` gives their units, gyms,
   own exercises, routines and recent numbers; without numbers, leave weights out.
3. `node tools/routine/liora-routine.mjs search <words>`: use catalog names exactly. Define `customExercises`
   only for what the catalog lacks.
4. Write `<name>.liora.json`, then `node tools/routine/liora-routine.mjs check <file>` until it says
   "Ready to import" (exit status 0), and read its warnings.
5. Tell the person where the file is and how to import it: open it with Liora on the phone, or Train › the
   import button › Open file (pasting the JSON there works too).
