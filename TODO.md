# To do

Bugs and polish that don't belong to a milestone yet. The roadmap lives in README.md.

## Bugs and polish

- **Health Connect: check on the phone** (added 2026-10-08). Settings › Your data › Health Connect › Set up:
  Health Connect's own permission screen shows Liora's three permissions, and its privacy policy link opens
  PRIVACY.md. Afterwards past workouts appear in Health Connect (and Samsung Health) as strength training,
  and a weigh-in from Samsung Health or a scale shows in Body (on a day without one of its own). Rename and
  delete a past workout: Health Connect follows when Liora next comes to the front.

- **Google Drive sync: the rest of the device check** (added 2026-10-08). Connecting and syncing between
  the debug and release apps on the Fold worked on 2026-10-08. Still to see:
  - Log a workout, leave the app: it syncs again (the time updates), and the other app gets it.
  - Photos arrive in the other app, and a photo deleted in one disappears from the other.
  - Disconnect, then connect again; turn the network off and on (a quiet "couldn't be reached" while off).
  - After a week or two of use: the twice-daily sync keeps going without opening the app.

- **Rest sound: check on the phone** (added 2026-10-03). The sounds were made without listening to
  them, so pick one in Settings › Workout and say if it needs to change (`tools/sound/MakeSounds.java`).
  - Let a rest run out locked, with the phone on vibrate, and with music in headphones. It plays at media
    volume. Music should duck while Liora is on screen; in the background Android 15 and up don't let
    an app duck others, so it plays over the music.
  - The old "Rest over" channel is replaced by one that only vibrates, so any changes made to it in the
    system settings are gone.
- **Notes: check typing on the cover screen.** Add an exercise note and a note for the workout from the
  card's menu: the keyboard comes up, the number pad goes away, and the note stays above the keyboard.

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
- **Motion (Phase 2c): check on the phone.** Only the device tells whether it feels good:
  - Tick a few sets quickly on the cover screen: the check's pop and the row's fill never hold up the
    next tap, and the haptic tick lands with the tap rather than after it.
  - Log a set that beats last time: the double beat reads as a record, not as a stutter.
  - Tap the tick on a set with an empty field and no placeholder: a buzz, and the cell opens.
  - Watch the duration, volume and sets roll in the header, the finish sheet's totals roll in, and the
    rest countdown roll; let a rest run out with the logger open and watch the last five seconds pulse.
  - Turn on "Remove animations" (Settings › Accessibility): everything shows its end state at once.
- **Motion (Phase 2d): check on the phone.**
  - Beat last time on a set: the trophy pops and the confetti reads as a celebration, over the rows
    around it, and is gone before the next tap. Finish: the records card bursts once.
  - Open a workout from History, a routine from Train and an exercise from the library on the cover
    screen: each grows from its card or row, and shrinks back on the way back. Nothing flickers at the
    floating tab bar.
  - Swipe back slowly from either edge and let go halfway: the screen follows the finger and comes back.
    Do the same in the logger: it sinks under the finger.
  - Switch tabs: a quick fade, not a slide. Open the logger from the workout bar: it rises.
  - Unfold and fold on a detail screen and in the logger: a crossfade, with the place kept. On the inner
    screen, open a few items from a list: each arrives in the detail pane.
- **Robolectric: a text field in a dialog never lets Compose go idle.** Find the cause, so the rename
  and save-as-routine dialogs can get UI tests.
