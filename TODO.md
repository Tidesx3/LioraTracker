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
- **Backups: check on a device.** Save one to Google Drive and to the phone, restore it after a reinstall,
  and time it with a real photo library. Tests cover the format, the merge and the ZIP through `file://`
  locations; real document providers (Drive in particular, and its handling of the `"wt"` write mode)
  are only seen on a device.
- **Imports: check on a device.** Import a real Hevy export (workouts and measurements) from the phone and
  from Drive, then a newer export of the same account, which should add only the new workouts. Tests cover
  the parsing, matching and writing with synthetic files; real document providers are only seen on a device.
- **Strong exports from a German phone are untested.** The parser reads Strong's English column names in
  both layouts, with decimal commas. If Strong localizes its headers, the file is reported as "not from Hevy
  or Strong"; a sample would tell which names to add (`StrongCsv`).
- **Violet: check on the phone.** The workout notification's tint in light and dark, the rest pill's
  outline counting down, the floating tabs over long lists, and Sora's numbers on the cover screen.
- **Logger in pounds: the "Previous" column cuts off** converted weights such as "176.37 lb × 10". Round
  converted values for display, or give the column more room.
- **Robolectric: a text field in a dialog never lets Compose go idle.** Find the cause, so the rename
  and save-as-routine dialogs can get UI tests.
- **Finishing a workout from a routine: show what the update changes.** The finish sheet already offers
  "Update “Push” with today’s weights and reps" (ticked by default) whenever the routine would change, and
  the update does take on replaced exercises and added or deleted sets (`RoutineUpdate.fromWorkout`). But
  the label only mentions weights and reps, so a swapped exercise or a different number of sets isn't
  visible when deciding. Name the changes instead, one row or chip each (e.g. "Bench Press → Dumbbell Bench
  Press", "Incline Press: 4 sets, was 3"), and keep the plain label when only values changed.
  - Decide with it: a planned set that was left unlogged drops out of the routine just like a deleted one.
    A set skipped on a tired day probably shouldn't shrink the plan; one that was deleted should.
