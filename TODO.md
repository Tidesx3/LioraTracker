# To do

Bugs and polish that don't belong to a milestone yet. The roadmap lives in README.md.

## Bugs and polish

- **Liquid Glass: check on the phone** (added 2026-10-11). The screenshots come from Robolectric, so the
  phone has the last word, on the release build:
  - Scroll the Train tab and History under the tabs: the glass follows the content as it moves (never a
    still picture of it), bends it at the rim, and scrolling stays smooth.
  - Pick tabs one after another: the drop flows over, stretching on the way, and settles. Drag along the
    bar: the drop lifts and follows the finger with a tick over each tab, and letting go switches there.
  - The Exercises tab's Custom exercise button sits above the tabs.
  - Top bars: the back arrow on a glass circle, the other buttons in a glass capsule (Save in the editors);
    the logger's Finish is violet glass beside them.
  - Buttons, chips, fields and the pad's keys look like one family; the main buttons (Start empty workout,
    Start workout, Repeat workout, Finish, Log set) are glass, the bottom ones floating over the content.
    Dialogs, sheets and menus: solid with a rim of light, nothing showing through. Snackbars (delete a
    workout, Undo) float as glass.
  - The top bars: content frosts over as it slides under the title and the status bar.
  - Tick a set: the rest timer floats as violet glass over the end of the list, its ring running. On the
    inner screen and in tabletop, the ring runs in a glass disc, with glass pills for −15 s, +15 s and Skip.
  - Light and dark, Colors from the wallpaper, the inner screen (the rail on the aurora), and Liquid Glass
    off (the solid look as before). Predictive back and a card growing into its workout.

- **Routines from an AI: check on the phone** (added 2026-10-11). Ask the Claude or ChatGPT app for a routine
  (Train › the import button › Copy the format for your AI, pasted into the chat), then:
  - Share its answer to Liora: Liora is in the share sheet as "Import routines", and the review opens.
  - Copy the answer and tap Paste; Android says Liora read the clipboard.
  - Make a file with the tool on the computer (`/liora-routine` in Claude Code), send it with Quick Share, and
    open it from the notification or Files with Liora; also pick it with Open file, and from Google Drive.
  - A broken one: Copy for the AI, paste it back into the chat, and the fixed file imports.
  - Done opens the routine, or the Train tab for a program; the rotation is up next; start it.
  - On the inner screen, and in German.

- **Updates from GitHub: the rest of the device check** (added 2026-10-10). 0.4.0 → 0.4.1 worked in the
  release app (checked 2026-10-10: Android asked, installed it, data kept; Liora is now its own installer).
  - The next release (0.4.2 on): Liora should update without Android's dialog (Android 12 and later).
  - With a workout running (Update waits), offline (Try again), and Not now (only Settings offers it).
  - Move to the release app: Back up in the debug app, Restore in the release app. Restoring merges, so clear
    the release app's own test data first if it has any.
- **Home-screen widget: check on the phone** (added 2026-10-09). Add Liora's widget on the cover and the
  inner screen, at two cells wide and at four by two. With a rotation it says what's next, and Start starts
  it and opens the logger; without one, Quick start. During a workout it shows the time running and the
  sets done; tick a set off and the rest counts down in violet, then the workout's time comes back when it
  runs out, also with the app closed. Try it light and dark, and with Colors from the wallpaper (Settings ›
  Appearance). The corners should follow the launcher's.
- **Rotations: check on the phone** (added 2026-10-09). Make a folder a rotation (its ⋮ › Make it a
  rotation): the Train tab heads with the routine up next and marks it in the folder. The history imported
  from Hevy counts by the workouts' names, so the next one should follow the last Push/Pull/Leg day logged.
  Finish a workout from it and see the next one come up; start another than the next and see it move on
  from there.

- **Double progression: check on the phone** (added 2026-10-09). Turn it on for a few machine and cable
  exercises in a routine (Edit › an exercise's ⋮ › Double progression) and give their sets rep ranges.
  - After a workout where every set reached the top: routine detail shows a step heavier and why, and
    starting the routine fills the weights in with the reps to aim for greyed.
  - A machine whose stack moves in its own steps: set it on the exercise's page (Weight step), then check
    the target and the logger's ±.
  - The history imported from Hevy counts for a routine that has no workouts of its own yet.
  - "Update the routine" at the end leaves exercises with progression as they were.
  - Settings › Progression: try a few choices and see routine detail's targets and reasons follow
    (checked on the phone 2026-10-09: the logger, routine detail and the weight step dialog, before the
    settings existed).
  - Set the cables' own steps: the pushdown is logged at 31,5 kg, off the gym's 5 kg grid.

- **First-launch walkthrough: see a real first start** (added 2026-10-09). Opened from Settings it works on
  the phone (checked 2026-10-09). Still to see, on a fresh install (say, the next time the release app is
  reinstalled): it shows before the tabs, with no flash of the Train tab first.

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
  - The Sync screen: "N changes waiting" after logging something, "Everything is in Drive" after a sync,
    and "Space in your Drive" with a size close to what Drive's Manage apps shows for Liora.
  - Disconnect Drive in Google's account settings (third-party access): the next sync asks to sign in
    again, the Train tab's gear gets its dot, and Sign in on the Sync screen clears it.

- **Rest sound: check on the phone** (added 2026-10-03). The sounds were made without listening to
  them, so pick one in Settings › Workout and say if it needs to change (`tools/sound/MakeSounds.java`).
  - Let a rest run out locked, with the phone on vibrate, and with music in headphones. It plays at media
    volume. Music should duck while Liora is on screen; in the background Android 15 and up don't let
    an app duck others, so it plays over the music.
  - The old "Rest over" channel is replaced by one that only vibrates, so any changes made to it in the
    system settings are gone.
- **The workout summary and the ±15 s click: check on the phone** (added 2026-10-10).
  - Finish a workout with a record on the cover screen and on the inner screen: the summary rises in the
    logger's place, the totals roll in, the records card bursts once, and Done (or back) lands where the
    workout was started. Say whether it should take more from Hevy (sharing, a streak, the muscles worked).
  - With the phone locked, tap −15 s and +15 s on the workout notification: each gives one click. It's a
    notification vibration, so it's off in silent mode and with notification vibration turned off.
- **Notes: check typing on the cover screen.** Add an exercise note and a note for the workout from the
  card's menu: the keyboard comes up, the number pad goes away, and the note stays above the keyboard.

- **Exercise picker: check fast scrolling on the phone** (reported 2026-09-30, fixed 2026-10-08 without a
  trace). Thumbnails asked Coil for a size only after layout, so a row scrolling back showed its initial
  for a frame before the photo, and photos decoded larger than needed. They now ask for their own pixel
  size (`ExerciseThumbnail`): cached photos show at once and many more fit. If flinging still stutters,
  take a trace of a fast fling and count recompositions in the Layout Inspector.
- **Save as routine: check the name dialog on a device.** UI tests can't drive it (see the Robolectric
  gotcha in CLAUDE.md); saving itself is covered in `core:data`.
- **Editing a past workout: check the start and end time pickers on a device.** UI tests drive the date
  picker only. The time math is covered in `core:domain` (`WorkoutTimesTest`), the writes in `core:data`.
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
    around it, and is gone before the next tap. Finish: the records card on the summary bursts once.
  - Open a workout from History, a routine from Train and an exercise from the library on the cover
    screen: each grows from its card or row, and shrinks back on the way back. Nothing flickers at the
    floating tab bar.
  - Swipe back slowly from either edge and let go halfway: the screen follows the finger and comes back.
    Do the same in the logger: it sinks under the finger.
  - Switch tabs: a quick fade, not a slide. Open the logger from the workout bar: it rises.
  - Unfold and fold on a detail screen and in the logger: a crossfade, with the place kept. On the inner
    screen, open a few items from a list: each arrives in the detail pane.
- **Robolectric: a text field in a dialog never lets Compose go idle.** Find the cause, so the rename
  and save-as-routine dialogs can get UI tests. Narrowed down on 2026-10-08:
  - It isn't idling that's stuck but a layout loop: with the logger's rename dialog open, every
    Choreographer frame runs another traversal of the dialog window (`DecorView.onMeasure` → the
    AlertDialog's content), so Espresso's drain of the main looper never ends.
  - An AlertDialog with an OutlinedTextField on its own goes idle, also in an edge-to-edge activity with
    `adjustResize`. So something on Liora's screens behind it sets it off; next suspects are the logger's
    keyboard and number pad handling (insets, focus) and the duration that ticks every second.
