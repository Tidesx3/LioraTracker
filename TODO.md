# To do

Bugs and polish that don't belong to a milestone yet. The roadmap lives in README.md.

The device checks for everything up to 0.4.10 were done on the phone and pass (2026-10-19); their lists are in
this file's history.

## Bugs and polish

- **Benchmarks: the first run on the phone** (added 2026-10-19). Everything builds and the made-up training
  restores in tests, but nothing has run on the Fold yet (README › Measuring speed):
  - `./gradlew :app:android:generateBaselineProfile`, folded, then commit `app/android/src/release/generated/`.
  - `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`, then
    `node tools/benchmark/results.mjs --save 0.4.10-cover`, and commit `benchmark/results/`.
  - Check that `assets/dexopt/baseline.prof` is in the next release APK, and see what the numbers say about
    startup's two blocking reads in `MainActivity` and about Liquid Glass's cost.

- **Strong exports from a German phone are untested.** The parser reads Strong's English column names in
  both layouts, with decimal commas. If Strong localizes its headers, the file is reported as "not from Hevy
  or Strong"; a sample would tell which names to add (`StrongCsv`).
- **Robolectric: a text field in a dialog never lets Compose go idle.** Find the cause, so the rename
  and save-as-routine dialogs can get UI tests. Narrowed down on 2026-10-08:
  - It isn't idling that's stuck but a layout loop: with the logger's rename dialog open, every
    Choreographer frame runs another traversal of the dialog window (`DecorView.onMeasure` → the
    AlertDialog's content), so Espresso's drain of the main looper never ends.
  - An AlertDialog with an OutlinedTextField on its own goes idle, also in an edge-to-edge activity with
    `adjustResize`. So something on Liora's screens behind it sets it off; next suspects are the logger's
    keyboard and number pad handling (insets, focus) and the duration that ticks every second.
