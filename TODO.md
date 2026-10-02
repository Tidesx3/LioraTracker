# To do

Bugs and polish that don't belong to a milestone yet. The roadmap lives in README.md.

## Bugs and polish

- **Exercise picker: fast scrolling stutters, and photos seem to load again** (reported 2026-09-30 on
  the Fold 7). Scrolling the list of exercises quickly, e.g. when adding one to a workout, isn't smooth
  any more. Thumbnails look like they're loaded again each time a row comes back into view. Places to
  look:
  - Measure first: recomposition counts in the Layout Inspector, and a trace of a fast fling.
  - Photos: whether Coil's memory cache hits when rows scroll back (the app uses Coil's default
    loader), and whether thumbnails decode at their small size or at the photo's full size.
  - Stability: `Exercise` comes from `core:model`, which has no Compose compiler, so rows that take one
    can't skip recomposition. A Compose stability config for `app.liora.core.model` may fix that.
    `ExerciseThumbnail` moved from the exercises feature to `core:ui` in `deca2e8`.
  - The lazy list: stable keys and a `contentType` for rows versus headers.
- **Workout notification: the header countdown runs 1–2 s behind the title.** Android ticks the header
  itself and rounds down; the title ("Rest 1:23") rounds up and updates twice a second. Make them agree,
  or drop the time from the title.
- **Save as routine: check the name dialog on a device.** UI tests can't drive it (see the Robolectric
  gotcha in CLAUDE.md); saving itself is covered in `core:data`.
- **Editing a past workout: check the start and end time pickers on a device.** UI tests drive the date
  picker only. The time math is covered in `core:domain` (`WorkoutTimesTest`), the writes in `core:data`.
- **Exercise library: the "New custom exercise" button has no accessible name.** Material's
  `ExtendedFloatingActionButton` clears its text's semantics, so TalkBack announces only "Button". Body's
  button sets a content description to fix this (`BodyScreen`); do the same here.
- **A photo taken while Android kills Liora is lost.** Opening the camera puts Liora in the background,
  and on a phone short of memory Android may end the process. The back stack isn't restored after that,
  so the gallery that asked for the photo isn't there to receive it. Saving the navigator's back stack
  (it's serializable route keys) would fix this, and other screens too.
- **App UI tests: an occasional failure cascades through the rest of the run** (seen 2026-10-02, once in
  several runs). `WorkoutNotifier` posts from `LioraApplication`'s process scope on `Dispatchers.Default`,
  and that scope outlives each test. A post that lands after Robolectric tore a test down throws (null
  `ActivityThread` in `checkSelfPermission`). The next test then fails before its `@After`, so Koin is
  never stopped, and every later test in that JVM fails with "A Koin Application has already been
  started". Fix: let tests cancel the process scope when Koin stops (e.g. hold it in Koin with `onClose`).
- **Robolectric: a text field in a dialog never lets Compose go idle.** Find the cause, so the rename
  and save-as-routine dialogs can get UI tests.
