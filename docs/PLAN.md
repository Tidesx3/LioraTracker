# Liora: workout tracker ecosystem plan

The full plan, agreed on 2026-09-29. README.md has the short roadmap; this file has the specs for each milestone.
Check it when a milestone or phase starts, and keep **Progress** below current when one lands.

## Progress

As of 2026-10-19:

- **Phase 0** is done.
- **Phase 1**, milestones 1–5 are done: database and seed, domain, exercises and foldables, routines, and the logger
  with the live workout notification.
- **Milestone 6 (history)** is done:
  - **6a** (`b763772`): list and calendar, workout detail, repeat a workout, save it as a routine, delete.
  - **6b** (`5e5ea43`): edit past workouts. A finished workout opens in the logger (`EditWorkoutRoute`): sets,
    exercises, supersets, notes and name, plus its date, start and end. Changes save as they're made, like live
    logging; Done drops sets that aren't logged, after asking. "Last time" and records look at the sessions before it.
- **Milestone 7 (progress)** is done, in three parts:
  - **7a is done** (`ceb2249`): per-exercise progress on the exercise page. A chart per metric
    (`ExerciseProgress.metricsFor`: e1RM, heaviest weight, volume and total reps for weight × reps; the fitting
    measures for other tracking types), best and latest values, the exercise's records and rep maxes, its past
    sessions (each opens its workout), and stall detection (`Stalls`, 3 weeks) with a badge. Charts use Vico behind
    `LineChart` in `designsystem`.
  - **7b** (`6d7e7a9`): the Progress tab. The weekly streak (`TrainingCalendar.weekStreak`) and this week's
    workouts and sets, a 16-week consistency grid, sets per muscle week by week with a body heatmap
    (`BodyHeatmap` in `core:ui`, a figure drawn in code), and the bests board (`RecordsBoard`: each exercise's
    standing best in its main metric, stalls flagged). On the inner screen an exercise opens beside the overview.
  - **7c:** the monthly report (`MonthlyReports`, `MonthlyReportRoute`), from a card on the Progress tab: workouts,
    training days, time, volume and sets against the month before, a calendar of training days, the records set,
    the most trained exercises, and muscles shaded by the average week. Months page back to the first with training.
- **Milestone 8 (body)** is done, in two parts:
  - **8a** (`f5c2e69`): bodyweight, body fat and 14 circumferences (`MeasurementType`), stored in the existing
    `measurement` table (no schema change) and kept to one value per type per day (`BodyRepository.saveDay`).
    The Body page (from the Progress tab) shows bodyweight with its chart, then every other measurement's latest
    value and its change over the month (`BodyMeasurements`). Each opens a page with its chart and every day,
    and a day opens the form to correct it. "Log measurements" is one form for a day, in pairs, with the last
    value as a hint; emptying a field removes that entry. On the inner screen a measurement opens beside the
    overview. Custom measurement types are left for later: they need a synced table for their names.
  - **8b:** progress photos, in the existing `progress_photo` table (no schema change). Taken with the camera app
    or picked with the system photo picker (`PhotoSource` in `core:ui`, no permissions needed), tagged front, side
    or back as they come in, and imported by `PhotoStorage` (`AndroidPhotoStorage`): turned upright, scaled to
    2048 px, saved as JPEG in app-private `files/photos`, and dated by the camera's EXIF date, so older photos land
    on their day. The gallery groups them by day with that day's weight, filters by pose, and opens a photo to
    correct its pose or day, or delete it. Compare puts two side by side (`ProgressPhotos.opening`: first and
    latest in the pose photographed last), with the time between them and the change in bodyweight; tapping a side
    and then a photo swaps it. Photos sync as blobs in Phase 3; until then `blob_id` stays empty.
- **Milestone 9 (settings, import and export)** is done, in five parts, each committed on its own:
  - **9a (done, `3ec369b`): settings and synced preferences.** `Settings` and `ThemeMode` in `core/domain`;
    `PreferenceDao` over the existing `preference` table (no schema change); `SettingsRepository` with
    `SettingsCodec` in `core/data/settings` (one row per choice; `update` writes only the choices that changed,
    so a new device can't overwrite another's with its defaults; `current()` is a suspend read that is safe
    inside a transaction). The Settings screen (`feature/settings`, from the Train tab's gear) has Workout (rest after
    working sets and after warm-ups), Progress (e1RM formula, stall window) and Appearance (theme, wallpaper
    colors on Android 12+, language on Android 13+ through `AppLanguage` in `core:ui`, which the OS keeps).
    The set logger reads rest defaults when a set is ticked off; the formula and stall window reach the
    logger's live records, exercise detail, history, workout detail, the Progress tab and the monthly report;
    `MainActivity` applies the theme from the first frame and styles the system bars to match.
  - **9b (done, `3519beb`): units and the RPE column.** `Units` in `core/model` (weight in kg or lb; distance in
    km and m, or miles and yards; body measurements in cm or in) and the `rpe` switch join `Settings` as
    preference rows (no schema change). Values stay stored in kilograms and metres. `MainActivity` provides `LocalUnits`
    (`core:ui` `UnitLabels.kt`), and every label converts for display: set summaries, progress values and
    records, body measurements, volume in history, the logger, the finish sheet and the monthly report, the
    routine editor and the workout notification. Charts plot converted values, so axis steps stay round. The
    logger, routine editor and body log type in the chosen units (± steps of 2.5 kg or 5 lb, 0.1 km or mi,
    10 m or yd). The RPE column (`SetField.Rpe`, after reps, only for exercises counted in reps) takes 1–10 in
    half points, is never needed to tick a set off and never pre-filled; a recorded RPE also shows in workout
    detail ("· RPE 8"), even with the column switched off.
  - **9c (done, `1a6aaf3`): gym profiles.** `GymProfile` in `core/model`: a name, the unit its equipment is labelled in,
    barbell and EZ bar, plate pairs, dumbbell runs and the stack step, with weights stored in kg.
    `GymProfiles.standard(unit)` in `core/domain` stands in until a gym is set up. `LoadRounding` rounds down
    to what the gym can load by equipment (plates for barbells and EZ bars, the dumbbell rack, the stack for
    machines and cables, else two of the smallest plate), finds the next loadable weight, and gives the plates
    per side. It works in thousandths of the gym's own unit, so pound plates add up exactly.
    `WarmupGenerator` rounds through it, with no bar step for dumbbells and machines. Gyms live in the synced
    `gym_profile` table (schema v3, `AutoMigration` 2→3 with a migration test), behind `GymProfileRepository`
    in `core/data/gym`. The gym in use is kept per device in `local_meta`; without a choice it's the first gym,
    else standard equipment in the display unit, and a new gym becomes the one in use.
    - Settings has a Gym row that opens the gyms (`GymsRoute`): pick the one in use, add one, open one in
      the editor (`GymEditorRoute`) for its name, unit, bars, plates with pairs, dumbbell runs and stack
      step, or delete it. A new gym switched to pounds swaps to the standard pound equipment.
    - In the logger, ± on a weight goes to the next weight the gym can load for the exercise's equipment,
      replacing 9b's fixed 2.5 kg and 5 lb steps. For barbell and EZ bar exercises, the plates per side
      show above the pad, with the total when the weight can't be loaded exactly. With two or more gyms,
      a switch sits on the same line. The exercise menu offers "Add warm-up sets" for weight × reps
      exercises that have no warm-ups and nothing logged yet. It ramps to the first working set's weight or
      placeholder, and `SetLogger.addWarmups` puts the sets before the working ones.
  - **9d (done, `bfd25c7`): backup, restore and the CSV export.** Settings has a "Your data" section with three rows,
    each going through the system file picker (`rememberFileSaver` and `rememberFileOpener` in `core:ui`, the
    Storage Access Framework, no permissions).
    - **Back up everything** writes one ZIP file: `liora.json` and each progress photo's image under
      `photos/<id>.jpg`. The JSON (`BackupFormat.kt` in `core/data/backup`, format version 1) holds custom
      exercises, exercise settings, routine folders and routines, finished workouts, body measurements,
      photo rows, gyms and settings. Workouts and routines nest their exercises and sets; times are ISO 8601,
      kinds are the stable keys, and units are in the field names. Every row keeps its id, creation time and
      HLC. Built-in exercises, the workout in progress and device-local state stay out.
    - **Restore a backup** reads the file, shows what it holds and when it was made, and on confirmation
      merges it row by row like sync will: a row missing here is added, a newer version replaces the one
      here, and anything changed or deleted here since stays (`BackupRepository.restore`). Restored rows keep
      their HLC and the clock moves past them (`SyncStamper.restored`); images are restored through
      `PhotoStorage.restore` before the rows that point at them. Restoring the same file twice changes nothing.
    - **Export as CSV** (`WorkoutCsv` in `core/domain`): one row per logged set, RFC 4180 with dot
      decimals, weights and distances in the user's units named in the header, local times, exercise names
      in the app's language. Since 2026-10-10 it first asks what goes in the file: the workouts, the body
      measurements (`BodyCsv`: a row per day, a column per measurement taken, in the user's units), or both
      in one file (`WorkoutCsv.formatWithBody`: a `record` column says `set` or `body`, in the order they
      happened).
    - The platform side is `ExportFiles` (`AndroidExportFiles`: ZIP archives through the content resolver).
      Backups and restores finish even when Settings closes mid-way.
  - **9e (done): Hevy and Strong import.** "Import from Hevy or Strong" in Your data opens a CSV through the
    file picker, shows what it holds (`ImportRoute`, `feature/settings/importing`), and imports it on
    confirmation. No schema change.
    - **Reading** (`core/domain/importing`): `ImportReader` tells the files apart by their columns. Hevy's
      workouts (`HevyCsv`: dates in the phone's language such as `30 Sept. 2026, 17:39`, `weight_kg` or
      `weight_lbs`, set types, supersets, notes) and measurements (bodyweight, body fat and every
      circumference, one value per type and day), and Strong's workouts in both layouts (`StrongCsv`: Set
      Order's W/D/F, rest-timer rows skipped, per-row units in the older layout). `Csv` handles quotes,
      line breaks in notes, semicolons and decimal commas; `ExportDates` reads English and German month names.
    - **Matching** (`ExerciseMatcher`): a name is the same exercise when it has the same words as one of
      an exercise's names or aliases in any language and order, give or take small words and equipment words
      that agree with its equipment ("Bench Press (Barbell)", "Beinpresse (Maschine)"). Weight logged on a
      bodyweight exercise goes to its weighted variant. Any other name becomes a custom exercise, a variation
      of the closest built-in by shared words (Dice, at least a third), tracked to fit what was logged
      (`SetFields.trackingTypeFor`). A real German Hevy export matched 53 of 70 names; the rest were
      gym-specific machines.
    - **Review:** how many workouts, sets and days, what's here already, then "New exercises" and "Already
      here" with where each name goes. Tapping one offers a new exercise, the closest one, or any exercise from
      the picker. A file without units (Strong's newer layout) gets a kg/lb switch.
    - **Writing** (`ImportRepository`): one transaction, finishing even if the screen closes. Workouts that
      started in the same minute as one here, and measurements of a type on a day that has one, are here
      already, so a newer export adds only what's new. Sets are ticked off across the workout's time, and keep
      only the values their exercise has columns for.
- **Phase 1 is done**, and Phase 2 (design and motion polish) moved up to follow it on 2026-10-02 on request.
- **Phase 2** is planned in parts 2a–2e (see the Phase 2 roadmap below).
  - **2a (done): the look.** A design lab rendered eight looks on the real app, side by side and switchable
    on the phone; the user chose **Violet**, and the lab, the preview setting and the other fonts went.
    - Colors (`Color.kt`): #5504CF is the accent in light, on white cards over a pale lavender grey; in dark
      it fills bars and ticks on near black, and a lighter violet of the same hue (#A57BFF) carries text and
      buttons. Done sets took the violet (emerald since 2026-10-03), drop sets turn pink so they don't read as the accent, records stay
      gold. The workout notification takes the violet too (`liora_accent`).
    - Type: Sora (OFL, bundled), digits made equal with `tabularNumbers()` where numbers change live.
    - Shapes and parts: generous corners; cards are `LioraCard` (white on a soft shadow in light, a lighter
      shade in dark); round checks that fill in; the tabs and the rest timer float as pills, and the time
      left runs around the rest pill's outline.
  - **2b (done): screens.** One `Stat`/`StatRow` for every row of figures (equal columns,
    big numbers with small units, the label underneath, shrinking rather than wrapping), so stats line up
    from card to card; one `RecordsCard` listing each record with the value it was set at
    (`RecordHighlights` in `core/domain`), on workout detail, the finish sheet and the monthly report; history
    cards show the name, date, duration, volume and sets only; the consistency grid is drawn, so its rows
    line up; the monthly report's figures sit two to a row; an exercise's sessions list their sets as chips,
    alike sets counted; charts label their axes in Sora; the body heatmap is an anatomical figure (`0161ee1`).
    Empty screens are illustrated: `EmptyState` takes an `Illustration` (`designsystem/illustration`), one of
    eleven small scenes drawn in code on a soft blob of the accent (routines, a barbell, a calendar, a chart,
    search, a kettlebell, a scale, photos, plates, an unreadable file, a tick in confetti). Their colors are
    tints of the theme's, so they follow dark mode and wallpaper colors; `IllustrationsTest` renders them
    all on one sheet. The list-detail placeholders use their feature's scene. A last pass at phone size put
    the remaining flat lists on cards: Settings groups each section on one (`SettingsGroup`), and gyms,
    body measurements and the import review's names sit on cards too, their rows letting the card show
    through (`LioraCardDefaults.listItemColors`). Workout detail and routine detail give each exercise a
    card with its picture, a superset's cards sharing the accent outline as in the logger. The exercise
    thumbnail's tile is a tint of the accent, so it shows on cards.
  - **2c (done, pending the device check in TODO.md): motion in the logging loop.** Only drawing moves, so
    no tap waits for an animation.
    - Ticking a set: the check fills and pops (it swells past its size and settles, the tick springing up
      inside), and the done tint spreads across the row from the check (`SetMotion.kt`). Reopening draws
      it back. Sets already done when the logger opens don't move.
    - Odometer numbers: `RollingText` (designsystem) rolls each changed character, up as the number grows
      and down as it shrinks, lined up from the right; screen readers get it as one text. `Stat` takes
      a `StatMotion`: the logger's duration, volume and sets roll as they change, the finish sheet's
      totals roll in from blank as it opens, and the rest pill and ring roll their countdown.
    - The rest pill's outline and the ring swell and fade on each of the last five seconds.
    - Haptics (`LioraHaptics`, named by what happened): a tick when a set is logged, a buzz when a value
      is missing, and a heavier double beat a moment later when the set is a personal record. The
      ViewModel reports how each tick turned out (`tickOutcomes`), so the haptic follows the real
      result. Rest over already vibrates through its alert, in the app too, so it gets no second buzz.
    - "Remove animations": Compose plays every animation at the system's animator scale, so with it off
      they finish at once; nothing here loops on its own.
    - Tests: `RollingTextTest` (which way a change rolls) and screenshots partway through ticking a set
      with the test clock paused (`motion_tick_early`, `motion_tick_late`).
  - **2d (done, pending the device check in TODO.md): celebrations and navigation.**
    - Records: a set that breaks one gets its trophy popping in from nothing with a burst of confetti in
      gold, violet and pink (`ConfettiBurst` in designsystem, drawn past its bounds, seeded so screenshots
      hold still); the records card bursts from its trophy as it opens (`RecordsCard(celebrate = true)`), on
      the summary after finishing since 2026-10-10. Records already set when the logger opens don't celebrate.
    - Screens (`ScreenTransitions.kt`): going deeper slides the new screen a tenth of the way in from the
      end as it fades in, and back reverses it; tabs fade through; a fold or unfold, which changes the
      layout but not the screen, crossfades. 300 ms, down from the library's 700 ms crossfade. The
      `Navigator` notes which way it last moved (`lastMove`: forward, back or across tabs).
    - Predictive back (on by default at target SDK 36+): the screen shrinks and drifts toward the swiped
      side under the finger, the one beneath coming up behind it, and fades only near the end.
    - The logger rises from the workout bar and sinks back into it, under the finger with predictive
      back too (`risingScreen()` entry metadata in `core/navigation`).
    - List to detail: a history card, a routine card and an exercise row grow into the screen they open
      and shrink back into it (`rememberSharedContainer` with `SharedKeys`, over a `SharedTransitionLayout`
      around NavDisplay), on a single pane only. Beside a list, an opened item fades and slides into the
      detail pane (`ListDetail`'s `Arriving`).
    - Tests: `lastMove` in `NavigatorTest`, and screenshots partway through a card growing into its
      workout (`motion_open_workout`) and a record's confetti (`motion_record`).
  - **2e (done): voice.** Empty states, the end of a workout, records and the workout notification speak
    with a light, warm voice that still says what to do: "Nothing on the menu yet", "The bar is waiting",
    "Call it a day?", "3 new personal records!", "Break’s over". German is written on its own rather than
    translated: "Der erste Satz fehlt noch" (Satz as set and sentence), "Bitte recht freundlich" for
    photos, "Zugabe gefällig?" when every set is done, "Kein Drama" for an empty workout. Messages that
    delete, restore or explain stay plain.
- **Phase 2 is done**, pending the device checks for motion in TODO.md.
- **Phase 3 (Google Drive sync and Health Connect)** is planned in parts 3a–3d (see the Phase 3 roadmap
  below). The Google Cloud project is set up (2026-10-08).
  - **3a (done): the sync engine.** No schema change. A batch is a backup (`BackupFormat.kt`) with an optional
    `deletedAt` per row. `SyncedTables` (`core/data/backup`) reads the synced tables by `RowSelection` (live for
    a backup; changed since the last push for a batch, each row with its parents; everything for a new store)
    and merges a backup back, for restores and pulls alike. `SyncEngine` (`core/data/sync`) pulls first
    (images before rows, then the rows and the new cursor in one transaction, not dirty), then pushes the dirty
    rows as one batch with the images of the photos in it, then clears `dirty` through `SyncDao` where the HLC
    hasn't moved. `startOver()` makes the next sync pull from the start and push everything. Photos' images
    are blobs named by the photo's id, fetched only when the image isn't here; a photo deleted elsewhere
    loses its image here. The clock starts past `NEWEST_MERGED_HLC` after a restart, and `receive` no
    longer lets the counter overflow its four digits. `SyncEngineTest` runs two phones against an
    in-memory store; the phone helpers moved to `TestPhone.kt`.
  - **3b (done, pending the device check in TODO.md): Google Drive.** No schema change.
    - `SyncRepository` (`core/data/sync`): connect an account (a new one starts over; the same one again,
      after signing in again, carries on), disconnect (stops and gives back access; nothing is deleted),
      sync now, and one sync as the background work runs it, keeping what went wrong (`SyncProblem`:
      unreachable, sign in again, Drive full, a newer Liora elsewhere). Account and problem are device
      state in `local_meta`. Backends report failures as `SyncUnreachableException`,
      `SyncSignInNeededException` and `SyncStoreFullException`.
    - `sync/gdrive` (KMP, tested on the JVM): `RestAppDataFolder` calls Drive's REST API through Ktor (list
      page by page, multipart upload into the app data folder, a fresh token once on 401, failures mapped
      to the exceptions above); `DriveSyncBackend` lays out `changes-<device>-<seq>.json.gz` (gzipped),
      `snapshot-<device>-<seq>.json.gz` and `blob-<photoId>`, reads each other device's batches in order
      without skipping a gap, and compacts: past 100 batches the engine hands over everything
      (`wantsSnapshot`/`putSnapshot`), and the device deletes its files up to there. `GoogleDriveAccess`
      (Android) signs in through `AuthorizationClient`, looks up the account's email through Drive, gets
      tokens for background syncs (or says to sign in again), and revokes access.
    - App: `WorkSyncScheduler` (WorkManager, with network: a one-off sync and one every 12 hours) runs
      `SyncWorker`; `SyncTriggers` asks for a sync when a workout ends and when Liora leaves the screen.
      Google's consent screen comes through `LocalCloudConsent` (`core:ui`), which `MainActivity` provides
      from a `CloudConsentFactory`; tests swap in one that grants access at once, a fake scheduler and an
      in-memory store (`TestLioraApplication`).
    - Settings › Your data › Sync with Google Drive (`SyncRoute`): off, an illustrated invitation
      (`Illustration.Sync`) with Connect; on, the account, when it last synced, the problem if any (with Sign
      in when that's what it takes), Sync now and Disconnect, after asking.
    - Since 2026-10-09 (on request) it also shows the space Liora takes in the Drive: after each sync the
      backend adds up its files (`SyncBackend.usedBytes`), shown like Drive does ("2.4 MB").
    - Also since 2026-10-09: how many changes haven't gone up yet ("3 changes waiting", or "Everything is in
      Drive"), counted live by `SyncDao.observePending` like a push would take them; and when sync needs the
      user (sign in again, Drive full, a newer Liora elsewhere: `SyncState.needsYou`), a dot on the Train tab's
      Settings gear and the problem in red on Settings' Sync row. A Drive that's only unreachable gets neither.
  - **3c (done, pending the device check in TODO.md): Health Connect.** No schema change.
    - `HealthRepository` (`core/data/health`) trades with a `HealthStore`: finished workouts go out as exercise
      sessions keyed by the workout's id, with the HLC as their version, so an edit replaces one and a
      deletion removes it; which version Health Connect has is kept per workout in `local_meta`, since a
      workout synced from another phone can carry an older stamp. Weight and body fat come in through
      `BodyRepository.addMissing`, only for days without a value of their type, the day's last reading
      counting. Each part only runs while Health Connect allows it.
    - `integration/healthconnect` (Android): `HealthConnectStore` on the Jetpack client, reading through
      the changes API (a token per set of allowed types; a full read when it runs out or the set changes),
      skipping Liora's own records, sessions of at least a second. The manifest declares the three
      permissions and a `PrivacyPolicyActivity` for Health Connect's rationale links.
    - App: `HealthTriggers` trades when Liora comes to the front and when a workout ends (reading needs
      Liora on screen). `LocalHealthConnectScreens` (`core:ui`) opens the permission screen, Health Connect's
      settings and its store page.
    - Settings › Your data › Health Connect (`HealthConnectRoute`): not installed or out of date (with the
      store page), an invitation to set it up, or what goes out and comes in, with its settings and Turn
      off, after asking. Tests use `TestHealthStore`.
    - Progress: the Body card moved up, under the monthly report (on request).
  - **3d (done, checked on the phone from Settings; a fresh install's first start is in TODO.md): first
    launch.** No schema change. A walkthrough of five
    pages in place of the tabs, once per phone (`WalkthroughRepository`, `feature/walkthrough`): Welcome with
    kilograms or pounds, routines, logging with a demo set to tick off (the check and row fill now live in
    `designsystem/motion`), progress, and Google Drive sync with a way into the Hevy and Strong import.
    Installs that ran an earlier Liora skip it; Settings › About Liora › How Liora works shows it again.
    The spec is under the Phase 3 roadmap.
- **Phase 3 is done**, pending the device checks in TODO.md.
- **Fixes from TODO.md (2026-10-08/09),** each committed on its own:
  - The rest countdown in the notification header agrees with the title (it ended a second early on
    purpose: Android rounds down, the title rounds up).
  - Weights in pounds show to the tenth (`WeightUnit.fractionDigits`), and a record's trophy sits on the set
    number's corner, so the logger's Previous column fits on the cover screen.
  - Exercise thumbnails ask Coil for their pixel size, so cached photos show at once when a row scrolls back.
  - The navigator's back stack is saved with the activity (`SavedNavigation.kt`), so screens survive Android
    ending the process; a progress photo taken with the camera app meanwhile is no longer lost.
  - The custom exercise button has an accessible name.
  - The Robolectric dialog problem is narrowed down (a layout loop, see TODO.md), not solved.
- **Phase 4** is planned in parts 4a–4d (spec under the Phase 4 roadmap, worked out on 2026-10-09).
  - **4a (done, pending the device check in TODO.md): double progression.** Schema v4. In three commits:
    the engine (`Progression` in `core/domain`, with the safety bound), the data (`ProgressionRepository`;
    starting a routine fills in target weights and the reps to aim for; backups carry the rule, a machine's
    own step and the aim), and the app. The routine editor turns it on per exercise from its menu and asks
    for rep ranges; routine detail shows next time's targets and why; the logger says why under each
    exercise (`ProgressionNote` in `core:ui`); an exercise's page sets a machine or cable's own step (the
    logger's ± uses it too). Settings › Progression tunes it: the reps to aim for (by default a rep more on
    the weakest set, after trying it on the phone), when to step up, what a session below the range does,
    what counts as last time, and the jump limit.
  - **4b (done, pending the device check in TODO.md): rotations.** No schema change (the column came with v4).
    A folder's menu makes it a rotation; the Train tab heads with the routine up next in the one trained
    last, and marks the next routine in every rotation. Imported history counts by the routines' names.
  - **4c (done, pending the device check in TODO.md): the home-screen widget.** Glance 1.2, in
    `app/android/.../widget/`: the workout as it runs (its clocks are `Chronometer`s that tick by
    themselves), else the routine up next with Start, else Quick start, with the weekly streak. The widget
    follows its state while Glance keeps its session; `WidgetUpdater` wakes it when the state changes, a
    rest running out included. Start goes through `MainActivity`, which starts the workout in the
    process's scope and opens the logger. Its faces are screenshot from the real `RemoteViews`.
  - **4d (done, checked on the phone): the workout on the lock screen.** The workout notification was
    already nearly a Live Update; it meets Android 16's rules (read in AOSP; `WorkoutNotificationTest`
    checks them, since Robolectric's Android 16 predates them) and is now public on the lock screen. On the
    Fold it's promoted: the chip in the status bar, and the Now Bar on the lock screen.
  - **4e (done, checked on the phone): updates from GitHub.** Worked out with the user (2026-10-10):
    `version.properties` and the release script (`tools/release/release.mjs`), the check (`integration/github`,
    `UpdateRepository`), the download, checks and install (`app/.../update/`), and the card on the Train tab and
    in Settings › About Liora. 0.4.0 and 0.4.1 are published; on the Fold, the release app found 0.4.1 and
    installed it over 0.4.0 with its data, and Android now lists Liora as its own installer. Left for later
    releases: an update without Android's dialog (0.4.2 on), and the user moving to the release app (TODO.md).
- **Phase 4 is done** (2026-10-10). **Next:** to be decided with the user; Phase 5 (server, web) was moved
  back as not a priority.
- **On request (2026-10-10), after Phase 4:** no schema change.
  - **The workout summary**, after Hevy's: finishing opens `WorkoutDoneRoute` (`feature/logger/done/`) in the
    logger's place, once the finish has landed (`LoggerViewModel.finished`). A medal (`Illustration.Medal`), how
    many workouts are in the books, the workout's name, date and totals rolling in, its records with the
    burst (moved here from the finish sheet, which now only confirms), and each exercise's sets and best set
    (`ExerciseProgress.bestSet`: by its main metric, with a trophy if it set a record). Done, or back, returns
    to where the workout was started.
  - **The notification's −15 s and +15 s click** (`WorkoutHaptics`), as a notification vibration: Android drops
    touch vibrations from an app in the background, and silent mode turns it off.
  - **CSV export of body measurements**, alone or with the workouts in one file (see 9d).
- **On request (2026-10-11): routines from any AI.** No schema change. An early, local slice of Phase 6's
  "program generation": the AI runs wherever the person already uses one, and Liora imports what it writes.
  - **The format** (`docs/routine-format.md`, `liora-routine` version 1, public): one routine or a program in a
    new folder or rotation, with every choice the routine editor has (supersets by label, rest, set types,
    weight, rep ranges, time, distance, RPE, double progression, notes) and custom exercises, new or a variation
    of a catalog one (tracking, equipment, muscles, note, a machine's load step). Exercises are named, not
    id'd; values come as people write them ("80 kg", "8-12", "1:30", "5 km"). Bare weights need `weightUnit`.
  - **Reading** (`RoutineFileReader` in `core/data/importing`): finds the JSON in an AI's reply, takes comments
    and trailing commas, and reports every problem with where it is (`RoutineFileProblem`, errors and warnings).
    **Matching and writing** (`RoutineImportRepository`): names through the Hevy import's `ExerciseMatcher`,
    except that a planned target an exercise has no column for is left out rather than making a second one; a
    custom exercise of a name the person has is theirs, so importing twice doesn't duplicate; one transaction
    writes the new exercises, notes (only where the exercise has none), the folder and the routines.
  - **The app:** Train's import button (and Settings › Your data › Import routines) opens `RoutineImportRoute`
    (`feature/settings/importing`): open a file, paste, or copy the format for an AI chat (the app carries a copy
    of the doc; a test keeps them equal). The review is the Hevy import's, with the routines and what's worth
    knowing; problems list where each is, with a copy for the AI. Liora is a share target for text and an "Open
    with" for JSON (`SharedRoutines`, copied into the cache at once). Done opens the routine, or the Train tab.
  - **The tool** (`tools/routine/liora-routine.mjs`, Node, no dependencies): search the catalog, `check` a file
    as Liora reads it (a port of the reader and matcher; their tests share the doc's examples), and read a
    backup for the person's units, gyms, own exercises, routines and recent e1RMs. The same as an MCP server for
    Claude Code, Codex, Gemini CLI, Cursor and Claude Desktop (`tools/routine/README.md`); a Claude Code skill
    (`.claude/skills/liora-routine`) for the workflow; `AGENTS.md` points other agents at it. CI runs its tests.
- **On request (2026-10-11): Liquid Glass**, the look the app starts with, Violet kept; Settings › Appearance
  switches back to the solid look. No schema change; one new setting (`appearance.glass`).
  - **The glass** (`designsystem/glass`, our own, no library): `Modifier.glassSource` records the content
    beneath into a `GlassBackdrop` (a `GraphicsLayer`), and `Modifier.glass` draws it blurred, its colors
    lifted, and bent along the rim as by a lens (an AGSL shader on Android 13+; 12 only blurs; older phones get
    nearly solid glass), then a tint, a glow inside the rim and the light catching it at the top left and,
    softer, the bottom right. Tokens are `LioraTheme.glass` (`theme/LioraGlass.kt`).
  - **The aurora:** three soft glows, violet, pink and blue (the wallpaper's colors with those on), behind every
    screen, fixed to the window so panes and the rail share one. Cards are see-through panes over it
    (`glassPane`), their shadow kept outside them.
  - **Where it shows:** the tabs float as a pill of glass (`LioraTabBar`), the selected one in a drop that
    flows to the next tab, its front leading and its back following; the workout bar and the rest timer are
    the accent's glass, and screens reach beneath them (`LocalFloatingChromeInsets`), as the logger's list
    does beneath the rest timer. Top bars have no color of their own: what scrolls under them frosts over and
    fades out (`glassEdge`). Every screen is a `LioraScaffold`, which does this when glass is on.
  - **Tests:** `LiquidGlassTest` (light and dark, cover and inner screen, the switch); flow tests that launch
    `MainActivity` render glass, those that set their own `LioraTheme` the solid look.
  - **After 0.4.5, on request:** the tab bar's drop can be dragged: it lifts and follows the finger, magnifying
    the tab under it with a tick (`LioraHaptics.tabCrossed`), and letting go picks that tab. Top bars put
    their buttons on glass, the navigation icon on a circle and the actions in one capsule, the logger's
    Finish outside it (`LioraTopAppBar(primaryAction)`). The rest ring on the inner screen and in tabletop
    runs inside a disc of glass, its buttons glass pills. Fixed: a screen's floating button (the Exercises
    tab's Custom exercise) hid behind the tabs, as Material places it by the window's edge;
    `LioraScaffold` lifts it.
  - **One look across the app (on request):** Liora's own buttons, chips, fields, dialogs, sheets, menus,
    snackbars and bottom bars (`LioraButtons.kt`, `LioraFields.kt`, `LioraOverlays.kt`) replace Material's
    everywhere. Floating parts are glass that bends; the main action is the accent's glass (in a bar, bending
    what's beneath); buttons, chips, fields and the pad's keys on the page are panes with the rim light
    (`glassRimBrush`, a border Material takes); dialogs, sheets and menus are solid panels with the rim. The
    detail screens' bottom bars lose their strip, their button floating. With glass off, all as before.
  - **Fixed after 0.4.7:** the tabs' lists ended behind the tabs (Train's last Start, Progress' last best): the
    shell consumes the room the tabs take, so Material left it out of the insets `LioraScaffold` added it to.
    It goes into the content's padding instead (`LiquidGlassTest.theLastRoutinesStartClearsTheTabs`).
- **On request (2026-10-14): motivation**, worked out with the user, in three parts, each committed on its own.
  No schema change; part 3 adds one preference row.
  - **Why:** the streak counts any week with a workout, which says nothing to someone who trains four or five
    times a week, and only records get celebrated, which come more rarely the longer one trains. In 8–20 rep work on
    machines and cables, the wins that keep coming are reps and steps up.
  - **Part 1, a step earned (done):** double progression's reward.
    - **The rule:** a session's sets earned a step when the engine, given them as last time, steps up.
      `Progression.earned` runs `Progression.next` on that session alone and reads the reason: `TopReached`,
      `TotalReached` or `FirstSetReached` give `Earned.StepUp(from, to)`, and `NothingHeavier` gives
      `Earned.TopOfTheStack`. There's no rule of its own, so the celebration always agrees with what the next
      start fills in, and follows Settings › Progression.
    - **Where it's worked out:** `ProgressionRepository.earned(routine, exercises)`. It runs for each of the
      routine's exercises with a rule (the first of its places speaks for it), on the workout's completed sets of
      that exercise, as the engine's sessions take them. Exercises without a rule whose working sets reached the
      top of every range the routine gives them are `readyForMore` (`TopOfRange.readyForMore`; ranges only, not
      exact counts, and only for weight and reps).
    - **In the logger:** a set that reaches the top of its range (its `targetReps`, a real range) gets a small
      violet chevron on its reps cell's corner, whatever the exercise. Under the progression note, "2 of 3 sets
      at the top" on the way there. When the step is earned, a banner in the exercise's card, under its sets:
      "Step earned! 32.5 kg next time", or "Maxed out! Nothing heavier than 50 kg here". It comes with a small
      burst in violet and emerald and a rising haptic, lighter than a record's. Steps already earned when the
      logger opens just show; when a record and a step come with the same tick, the record's haptic plays.
    - **In the summary:** a card after the totals with each step earned (from → to, or maxed out), and, quieter,
      the exercises without progression that reached the top of every range: ready to go heavier.
    - **Tests:** `earned` in the engine and `TopOfRange` (each step-up mode, a set short or not ticked
      off, the top of the stack, ranges against exact counts); the repository (on the way, earned and what the
      next start makes; ready for more without a rule); the logger and the summary in `ProgressionFlowTest`,
      with screenshots at cover and inner sizes and in German.
    - **As built:** the logger's `earned` flow asks the repository again whenever the workout's sets change, and
      is null until it first answers, so the card (`ExerciseCard`) takes the steps there on opening as already
      shown. The haptic is `LioraHaptics.stepEarned`, skipped within a second of a record's.
    - **The look, in Liquid Glass** (on request: new parts must look great and speak the app's language, now a
      rule in CLAUDE.md): the banner (`StepEarnedBanner`, `SetMotion.kt`) is a capsule of the accent's glass,
      like the main buttons, with its arrow on a bead of lighter glass (`StepBead`) that the burst flies from,
      and light sweeps across it once (`glassShine`); the chevron (`TopMark`) is a bead of the accent's glass
      with the rim's light. The summary's steps card is a violet pane with the rim, its arrow on a bead that
      pops in, the new weight on a pill of the accent's glass. `RecordsCard` (core:ui) became a gold pane
      with the rim beside it, so the summary, workout detail and monthly report read as one. With glass off,
      all of it is solid violet (`primaryContainer`, `primary`). `ConfettiBurst` takes other colors.
  - **Part 2, against last time (done):** racing last time, a small win almost every session.
    - **The rule** (`LastTimeComparison` in `core/domain`): each working set pairs with its counterpart as the
      Previous column pairs them (`SetPlaceholders.previousFor`); warm-ups don't count. A set is ahead when it's
      at least as heavy and at least as long (reps, time or distance) and more of one, behind the other way
      round, and level when the same. When one went up and the other down, weight and reps go by the e1RM
      (load times reps past 12 reps, where there's no estimate), other loads by load times length; less
      assistance is heavier, and a run of the same distance is ahead for being quicker. Only sets done count,
      so a workout isn't behind for being unfinished. `AgainstLastTime` holds each set's verdict, how many sets
      have a counterpart, and the volume (or, without load, the reps) the sets done add against theirs.
    - **In the logger:** a scoreboard pill under the totals, "⟲ Last time ▲ 1 of 3 │ +80 kg" (`RaceChips`,
      `RaceViews.kt`), on a `LioraStatusChip` (designsystem: a chip that's a fact, not an action, a glass pane
      like the others). Before the first tick, "3 sets to beat", so the list doesn't jump when the score starts;
      nothing when nothing has a counterpart. Screen readers hear one sentence ("1 of 3 sets ahead of last
      time, +80 kg"). After each tick, the Previous cell gets an emerald ▲, a quiet "=" or a quiet ▼, popping
      in like the check. Ahead is emerald like a set done; level and behind stay grey, never red.
    - **In the summary:** the same pill in the totals card, against each exercise's session before the workout
      (`WorkoutHistoryRepository.edit(id).previousSets`, the logger's own query).
    - **A switch** (on request, as a score can feel like pressure): Settings › Workout › Race last time, on by
      default (`Settings.raceLastTime`, the synced preference `logger.race_last_time`). Off, the logger and the
      summary show neither the pill nor the marks.
    - **Tests:** the rule for each tracking type and the workout's sums (`LastTimeComparisonTest`), the logger
      (`LoggerFlowTest.racingLastTimeScoresEachSetDone`) and the summary in English and German
      (`WorkoutDoneFlowTest`).
  - **Part 3, a weekly goal and rest weeks (done):**
    - **The goal:** training days a week (days, so splitting off a cardio workout doesn't count twice), 1 to 7.
      `WeeklyGoals` keeps each goal with the week it starts from (`GoalChange`), so a change never rewrites past
      weeks; before any goal, one day a week counts, as the streak always had it. Stored as one synced
      preference row (`goals.weekly`, "2026-10-12:4,…").
    - **The streak:** `WeekStreaks.of` (`core/domain/WeeklyGoals.kt`) replaces `TrainingCalendar.weekStreak`,
      counting weeks in a row that reached their goal; this week adds once reached and never ends it early.
      Every 6 weeks reached earn a rest week, at most 2 held; a week short uses one, which keeps the streak but
      doesn't add to it. `WeekProgress` also says how each past week went (`WeekResult`: met, rested, missed)
      and whether this week earned a rest week. Worked out from history and never stored.
    - **Where it shows:** `WeekProgressRepository` (`core/data`, a factory: it holds only the clock) feeds:
      - the Train tab's week card, in Quick start's list item, once there's a goal or any training: a ring
        filling toward the goal ("2/3", emerald once reached), a glass bead for each day trained with today
        outlined, the streak, rest weeks in hand, and "last week was a rest week" after one. A tap opens the
        goal picker (`WeekCard`, `WeeklyGoalDialog` in `core:ui`).
      - the summary: the same card, worded for after a workout ("Week goal reached!", "1 more day to go this
        week", "You earned a rest week").
      - the Progress tab: the streak by the goal, rest weeks in hand under the stats, and rested weeks tinted in
        the consistency grid.
      - the widget: "3 of 4 this week" above the streak, once there's a goal.
    - **Choosing it:** the walkthrough's Progress page asks how many days a week (3 suggested, nothing chosen
      until tapped); Settings › Workout › Weekly goal and a tap on the Train tab's card change it.
    - **Tests:** the streak, rest weeks, goal changes and days counted once (`WeekStreaksTest`), the goal from
      this week on (`WeekProgressRepositoryTest`), the card, the picker, light, inner screen and German
      (`WeeklyGoalFlowTest`, with a fixed clock), and the walkthrough's choice.
- **On request (2026-10-19): measuring speed.** No schema change. Speed was judged by eye on the release build;
  now it's measured on the phone, and the release build ships a baseline profile.
  - **The benchmark build:** the baseline profile plugin (`androidx.baselineprofile`, benchmark 1.5.0) adds
    `benchmarkRelease` (R8, to measure) and `nonMinifiedRelease` (to collect the profile from), copied from the
    release build. Both install as `app.liora.benchmark`, signed with the debug key, never look for updates, and
    carry `app/android/src/benchmark`: `BenchmarkDataActivity`, which the benchmarks start first. It restores a
    year of made-up training once (`BenchmarkTraining`: a push/pull/legs rotation on machines and cables four days
    a week with a bike warm-up and double progression, weeks with two days, two weeks at a holiday gym on its own
    variations, core and a run monthly, weigh-ins every few days, a goal of 4 days; one seed, dated back from
    today) through the real restore, then ends a workout left running and turns Liquid Glass on or off.
    `BenchmarkTrainingTest` restores it in the debug unit tests, which get those sources too.
  - **Benchmarks** (`benchmark/`, `liora.android.benchmark`, `com.android.test`): `StartupBenchmark`, a cold start to
    the Train tab with nothing compiled and with the profile; the Train tab reports fully drawn once loaded
    (`ReportDrawnWhen` in `core:ui`). `FrameBenchmark`, frame times scrolling each tab, switching tabs, opening a
    workout and an exercise's progress, and ticking five sets, each with glass on and off. They find screens by
    test tags (`testTagsAsResourceId` on the app's root), so they work in German and on either screen; the set's
    tick is `logger.check` while open, and a workout starts through the widget's Start.
  - **The baseline profile** (`BaselineProfileGenerator`): startup (also the startup profile, for dex layout) and
    the same journeys, collected on the phone by `generateBaselineProfile` and committed under
    `app/android/src/release/generated/`; release builds never wait for a device. `profileinstaller` installs it,
    as sideloaded Liora has no store to.
  - **Results:** `tools/benchmark/results.mjs` prints the medians and frame percentiles, saves a run to
    `benchmark/results/` and compares against one.
  - **Not run on the phone yet** (TODO.md): the first profile and the first numbers.
- **Roadmap reordered (2026-10-09, on request):** programs and progression, the widget and Wear OS move up to
  Phase 4; the self-hosted server and the web GUI move back to Phase 5, as they're not a priority now. AI
  stays Phase 6, since it runs on the server.
- **Release builds** (2026-10-08, `05d7ba2`): signed with a local key named in the gitignored
  `keystore.properties` (README › Release builds), smoke-tested with R8. Judge speed on them, not on debug.
- **After the first real workout (2026-10-03):** fixes from logging one on the phone. No schema change.
  - Done sets turn emerald (`LioraColors.completed`, #0E9F6E in light, #34D399 with a dark tick in dark),
    so done reads apart from the violet that marks what's next.
  - The logger has one list for all its layouts (`WorkoutListPlace`): it opens at the set that's up next
    (or the one logged last), from the workout bar too, and keeps its place when the phone turns or folds.
    Turned sideways, the cover screen is wide enough for two panes, which used to start a list of their own.
  - Rest over plays a sound Liora makes itself (`tools/sound/MakeSounds.java`, synthesized struck bars:
    chime, bell, marimba). Settings › Workout picks one or none, playing each as it's picked
    (`RestSound` in `Settings`). `AndroidRestSoundPlayer` plays it on the media stream from the rest alarm,
    so vibrate mode doesn't mute it and it comes through headphones, ducking music while Liora is on
    screen. The rest channel (`rest_end`, replacing `rest_over`) only vibrates.
  - Two notes per exercise. The exercise's own (`exercise_settings.sticky_note`, `ExerciseRepository.setNote`)
    is pinned under it in the logger, the up-next pane, routines and on its page, and the custom exercise
    editor writes it too. Each workout keeps its own (`workout_exercise.notes`), shown in history. Both edit
    in place on the card (`ExerciseNoteField` in `core:ui`). Starting a routine no longer copies its notes
    into the workout, and updating a routine no longer takes today's notes. `LegacyNotes` moves custom
    exercises' notes and routines' exercise notes onto the exercise's note at startup. An import makes the
    note from an exercise's last session its note, when it has none: Hevy copies routine notes into every
    workout, so one still there last time is the plan.
- **Working style:** one milestone at a time, built, tested (`./gradlew check`) and committed before the next.
  Big milestones split into parts, committed as e.g. "Phase 1 M6a: …". Bugs and polish that don't belong to a
  milestone go in TODO.md.

## Context

Liora started as a greenfield build on 2026-09-29. The goal is a workout logging ecosystem:
**an Android app first**, synced to the user's own Google Drive, then a **self-hosted server** with a **web GUI**, and later **AI features**.
It should be modular and clean to build on, with a sleek, fast UX that learns from Hevy, Strong and others and fixes their known pain points.
The user wants to own their data, with no paywalls: a logger as fast as Strong with Hevy's polish.

**The main user** trains in German and has logged in Hevy (German locale): a push/pull/legs split, mostly machines and
cables. They keep a separate custom exercise per gym and machine, and log cardio warm-ups as timed warm-up sets.
German copy and exercise names must feel natural to a German gym-goer, not like a translation.

**Decisions so far**
- **Stack:** Kotlin Multiplatform (KMP). The app is native Android in Compose. Domain, data and sync logic are written once and shared with a Ktor server and the web client.
- **Audience:** personal use and self-hosting first (sideload, Docker server, single user or family). Every record still carries an `ownerId`, so a public release stays possible.
- **v1 training types:** weights, bodyweight/assisted, and timed/cardio. Programs with auto-progression come later, layered on routines.
- **Early priorities:** body metrics and photos, Hevy/Strong import, Health Connect. A Wear OS app is deferred.
- **Languages:** English and German from Phase 1 onward. This covers UI text, number and date formats, and exercise names.
- **Custom exercises:** unlimited, created from the library or mid-workout, first-class everywhere (charts, PRs, routines, sync, import).
- **Form factors:** phones and foldables. The reference device is the Galaxy Z Fold 7: a cover screen of about 411 dp wide (compact) and an inner screen of about 984 × 1092 dp (expanded in both orientations). Tablets get the same adaptive layouts.

Don't propose features or stack changes that conflict with these decisions without asking first.

---

## What competitors do well, and where we improve

| Learned from | What to copy | How we improve |
|---|---|---|
| **Strong** | Fastest logging: previous values pre-filled, rest timer starts when a set is ticked, compact rows | Separate **warm-up and working rest durations** per exercise. Hevy lacks this and users call it a common frustration |
| **Hevy** | "Previous" column, set types (W/D/F), supersets, RPE, plate calculator, live PR banner, monthly report, sets-per-muscle charts, folders | **No paywalls.** Hevy free caps you at 4 routines, 7 custom exercises and 3 months of history, and only weight and waist measurements. **No forced social feed** (Hevy's Discover feed can't be disabled) |
| **Hevy (gaps)** | – | Only a one-time import from Strong, and no Garmin → we import **Hevy and Strong CSVs** at any time, offer **lossless JSON export**, and bridge through **Health Connect** |
| **FitNotes** | Offline, private, free, loved by its users | The same data ownership, with a modern UI |
| **Liftosaur / Alpha Progression / Hevy Trainer** | Programmed progression: the app tells you the next weight and reps | Later phase: a deterministic progression engine, with AI on top of it |
| **RP Hypertrophy** | Volume per muscle and feedback-driven mesocycles | Weekly sets per muscle in v1. Feedback-driven programs come later |

**UX principles** (every screen is checked against these):
1. **Logging speed is king.** One tap completes a set. Big touch targets. A custom in-app numeric keypad with ± plate-increment steps and a "next field" key (no system keyboard jumping the layout).
2. **Context inline.** Each set row shows last session's matching set, which you tap to copy. Sticky exercise notes (seat height, grip) appear every time.
3. **Never lose a workout.** The active workout is written to the database on every change, so it survives process death and reboots, with a "Resume" banner.
4. **The home screen is yours.** No feed. Show next routine, this week's volume, streak, and a resume or start button.
5. **Your data.** Unlimited everything, export any time, local-first, and sync to storage you own.
6. **Sleek.** Material 3 Expressive, dark-first theme with optional dynamic color, restrained motion, a subtle celebration for PRs, one-handed reach (primary actions at the bottom).

Sources: [Hevy features](https://www.hevyapp.com/features/), [Hevy gaps](https://aitoolsbakery.com/blog/hevy-review/), [Hevy free vs Pro](https://repreturn.com/hevy-pro-vs-free/), [Strong vs Hevy](https://repreturn.com/strong-app-vs-hevy/), [Reddit picks](https://setgraph.app/ai-blog/best-gym-app-reddit), [RP vs Alpha](https://alphaprogression.com/en/blog/best-rp-hypertrophy-alternatives), [free-exercise-db](https://github.com/yuhonas/free-exercise-db)

---

## Architecture

**Pattern:** offline-first with unidirectional data flow.
`Compose Screen ← UiState (StateFlow) ← ViewModel ← UseCases / Repositories ← Room DB`, and in the background `SyncEngine ↔ SyncBackend`.

**Dependency rules**
- Features depend only on `core/*`.
- Features never depend on each other. Navigation goes through route keys in `core/navigation`.
- `app/*` wires everything together.
- Nothing in `core` knows about Android unless it sits in `androidMain`.

### Repo layout (Gradle monorepo, version catalog, convention plugins)

```
LioraTracker/
  build-logic/convention/     # plugins: liora.kmp.library, liora.cmp.feature, liora.android.app, liora.jvm.server
  gradle/libs.versions.toml   # all versions pinned here (latest stable at scaffold time)
  core/
    model/        KMP   pure types: Exercise, Workout, WorkoutSet, Routine, Measurement, TrackingType, SetType, Muscle, Equipment, value types (Mass, Distance)
    common/       KMP   UUIDv7 ids, HybridLogicalClock, dispatchers, Result, time utils
    domain/       KMP   e1RM, PR engine, volume & per-muscle stats, stall detection, load rounding, plate calc, warm-up generator, progression rules, safety bound, unit conversion, importers (Hevy/Strong CSV), JSON export codec
    database/     KMP   Room (android + jvm targets), DAOs, migrations, exported schemas, exercise seed
    data/         KMP   repositories (write-through, mark dirty), DataStore prefs
    sync/         KMP   SyncEngine, ChangeSet, SyncBackend interface, LWW merge (reused by server)
    navigation/   KMP   route keys (Navigation 3)
    designsystem/ CMP   theme/tokens, SetRow, NumberPad, RestTimerChip, charts wrappers (Vico), empty states
  feature/                   CMP (Android target now; wasmJs later for web reuse)
    train/        home, routines & folders, routine editor
    logger/       active workout, rest timer UI, finish summary
    history/      list + calendar, workout detail/edit
    exercises/    library, search/filter, exercise detail (history, charts, PRs), custom exercise editor
    progress/     e1RM/volume charts, PR board, weekly sets per muscle, consistency
    body/         bodyweight, measurements, progress photos
    settings/     units, rest defaults, gym profiles, theme, backup/import/export, sync setup
  platform/android/          rest-timer notification + alarms, WorkManager sync jobs, file pickers (SAF)
  integration/healthconnect/ Android: write sessions, read bodyweight (Phase 3)
  sync/gdrive/               Android: Drive appDataFolder backend (Phase 3)
  app/android/               Application, Koin graph, NavDisplay host, manifest
  # later: core/api-contract (DTOs), sync/server, server/ (Ktor), app/web (CMP wasm)
```

**Libraries:** Kotlin + Compose Multiplatform, Room KMP (bundled SQLite driver), Navigation 3, Koin, kotlinx.serialization / coroutines / datetime, Ktor client, Vico (charts), Coil 3, Reorderable (drag lists), Kermit (logging), a KMP CSV reader.
**Tests:** kotlin.test, Turbine, kotest-property, Roborazzi. **Quality:** Spotless + ktlint, detekt, Android Lint, GitHub Actions CI.

### Data model (designed for sync from day one)

Every syncable row carries `id` (UUIDv7, created on the client), `ownerId`, `createdAt`, `updatedAt` (HLC), `deletedAt` (tombstone), and a local-only `dirty` flag.

- **Exercise**: `name`, `trackingType`, `equipment`, `primaryMuscles`, `secondaryMuscles`, `instructions`, `imageRef`, `isCustom`, `sourceId`, `variationOf`, `stickyNote`, `restWorkingSec`, `restWarmupSec`, `archived`.
  - `trackingType` is one of `WEIGHT_REPS`, `BODYWEIGHT_REPS`, `WEIGHTED_BW`, `ASSISTED_BW`, `REPS_ONLY`, `DURATION`, `DURATION_WEIGHT`, `DISTANCE_DURATION`, `WEIGHT_DISTANCE`. It decides which set columns appear.
  - `isCustom` marks user-created exercises. `sourceId` points to the seed entry for built-ins. `variationOf` optionally links a custom exercise to the built-in it was duplicated from, so its muscles and history grouping carry over.
  - Built-in exercises are read-only apart from per-user overrides: sticky note, rest timers and hide/archive.
- **ExerciseName**: `exerciseId`, `locale` (`en`, `de`), `name`, `isAlias`. Holds the localized name plus search aliases for each exercise, for example "Bankdrücken" with the alias "Bench Press". A custom exercise has one name in whatever language the user typed it.
- **RoutineFolder**, **Routine**, **RoutineExercise** (`position`, `supersetGroup`, rest override, `note`), **RoutineSet** (`setType`, target weight, target reps min/max, target duration or distance, target RPE).
- **Workout**: `name`, `startedAt`, `endedAt` (null means in progress), `routineId`, `notes`, `bodyweightKg` snapshot. Then **WorkoutExercise**, and **WorkoutSet** with `setType` (WARMUP/NORMAL/DROP/FAILURE), `weightKg`, `reps`, `durationSec`, `distanceM`, `rpe`, `completedAt`.
- **Measurement**: `takenAt`, `type` (BODYWEIGHT, BODY_FAT, WAIST, CHEST, ARM_L, … plus custom types), `value` in canonical SI units.
- **ProgressPhoto**: `takenAt`, `pose`, `localPath`, `blobId`.
- **GymProfile** (Milestone 9): `name`, bar weights, plate inventory (sizes and pairs), dumbbell steps, and the stack step for machines and cables. Which profile is active is a local preference.
- **Derived, not synced, recomputed locally:** `personal_record` and per-exercise stats caches.

**Canonical units:** kg, meters and seconds are stored. The display unit is a preference (kg/lb, km/mi).

**Exercise seed:** about 800 public-domain exercises from **free-exercise-db**, mapped at build time into our muscle and equipment enums and tracking types, and shipped as a bundled asset.
- The source data is English only. German names and common aliases are generated once into the seed file and reviewed.
- Instructions stay English at first and get translated later.

### Adaptive layouts (foldables first)

- **Principle:** layouts follow window size classes, not device checks. The same code serves the Fold 7 cover screen, the unfolded inner screen, split-screen and tablets.
- **Navigation:**
  - Compact widths get the bottom bar.
  - Medium and expanded widths get a navigation rail, with the active-workout card placed in the rail.
  - This uses Material 3 `NavigationSuiteScaffold`.
- **Two panes on the inner screen**, using Navigation 3 list-detail scenes:
  - Exercise library and exercise detail
  - History list and workout detail
  - Routines and the routine editor
  - Progress overview and chart detail
  - On compact screens the same destinations stack as today.
- **Logger unfolded:** exercise list on the left, the selected exercise's sets and number pad on the right, so no scrolling back and forth mid-set.
- **Tabletop posture:** when the Fold is half-open and standing on a bench, the logger puts the rest timer and current set on the top half and the number pad and tick button on the bottom half.
- **Continuity:**
  - Folding or unfolding mid-workout keeps everything: active workout, scroll position, half-typed values and the running timer.
  - No letterboxing, and no activity restart that loses state.
  - Resizable windows (split-screen, pop-up view) work at any size.
- **Content width:** reading-heavy screens (instructions, settings, forms) are capped at about 600 dp and centered, so text lines don't stretch across 1000 dp.

### Localization (English + German)

- **UI strings:**
  - Every string lives in `composeResources/values/strings.xml` (English, the default) and `values-de/strings.xml`.
  - Counted text uses plurals, e.g. "1 Satz" / "3 Sätze".
  - A build check fails `./gradlew check` when a key is missing from the German file, so no screen ships half-translated.
- **Language choice:**
  - The app follows the system language.
  - Settings offers System / English / Deutsch through Android 13+ per-app languages (`localeConfig`).
- **Formats:**
  - Numbers follow the locale, e.g. "82,5 kg" in German.
  - The custom number pad shows the locale's decimal separator and accepts both `,` and `.`.
  - Dates, weekday names and the first day of the week (Monday for `de`) come from the locale. They are used in the history calendar, streaks and the monthly report.
- **Exercise search:**
  - Matches names and aliases in both languages, since many German lifters use English names.
  - Ignores case and umlauts, so "Kreuzheben", "kreuzheben" and "Kreuzhebn" all find deadlifts.
- **Import:** Hevy and Strong CSVs exported on German-locale phones are accepted, with decimal commas and localized headers.

### Sync design (pluggable backends)

- **The local DB is the source of truth.** Repositories bump `updatedAt` using the HLC and set `dirty=1` on every write.
- `SyncEngine.sync()` does three things: it pushes dirty rows as a `ChangeSet`, pulls remote changes since the last cursor, and merges them. The merge rule is **last-writer-wins per row by HLC**, and tombstones win over older edits. The merge code lives in `core/sync` and is reused unchanged by the server.
- **`SyncBackend` interface:** `pull(cursor)`, `push(changeSet)`, `putBlob`, `getBlob`.
  - **Google Drive backend (Phase 3):**
    - Uses the `drive.appdata` scope: a hidden per-app folder that counts against the user's own quota.
    - Auth runs through Credential Manager and `AuthorizationClient`. The legacy GoogleSignIn API was removed in play-services-auth 22.
    - Calls the REST v3 API through the Ktor client.
    - Layout: each device only appends its own `changes/<deviceId>/<seq>.json.gz` batches, so there is no write contention. Any device can occasionally compact everything into `snapshot-<hlc>.json.gz`. Photos are stored under `blobs/`.
  - **Server backend (Phase 5):** `GET /sync/pull?cursor=` and `POST /sync/push` using the same ChangeSet DTOs. The server assigns a monotonic `serverSeq` that clients use as their cursor.
- Sync is triggered by WorkManager: when a workout finishes, periodically, and manually. It requires network.
- Switching backends means a full push of the local DB.
- **Manual backup** (JSON export and import via SAF) always works, independent of sync.

---

## Roadmap

**Phase 0 and Phase 1 are executed milestone by milestone.** Each milestone is built, tested and committed before the next starts. Phases 2–6 each get a short planning pass when we reach them.

### Phase 0: Environment and scaffold
1. **Install tools** (needs your OK, since this changes the system):
   - `winget install Google.AndroidStudio` and `winget install EclipseAdoptium.Temurin.21.JDK`.
   - You run the Android Studio first-run wizard (SDK + platform tools), or I use `sdkmanager`.
   - Use your phone with wireless debugging, or an emulator.
2. `git init` with a `.gitignore`, `README.md` and a `CLAUDE.md` describing the architecture and conventions.
3. Gradle wrapper, `libs.versions.toml`, the `build-logic` convention plugins, the module skeleton above, Spotless, detekt, and a CI workflow file.
4. App shell:
   - Theme with light, dark and dynamic color options.
   - Bottom nav: **Train · History · Progress · Exercises**, plus a settings gear.
   - A persistent **active-workout mini bar** above the nav, which expands to the full logger.

### Phase 1: MVP, fully offline
1. **Model, database and seed:** Room schema v1 with schema export, the HLC and UUIDv7 utilities, and the exercise seed import including the German exercise names.
   - **Localization groundwork:** German strings for everything built in Phase 0, the missing-translation check, locale-aware number and date formatting helpers in `designsystem`, and `localeConfig` for per-app language.
2. **Domain:**
   - e1RM: Epley by default, Brzycki selectable, counted only for sets of 12 reps or fewer.
   - PR engine: heaviest weight, best e1RM, best set volume, rep-maxes for 1–12 reps, longest duration or distance, best pace.
   - Weekly sets per muscle: primary muscles count 1, secondary 0.5.
   - Plate calculator (bar and plate inventory) and warm-up generator.
3. **Exercises:** search in both languages, filters by muscle and equipment, and an exercise detail page with history, charts, PRs and instructions.
   - **Custom exercises (unlimited):**
     - **Where to create:** from the library, or straight from the logger's exercise picker. When a search finds nothing, "Create '…'" pre-fills the name, and the new exercise is added to the running workout.
     - **Fields:** name, tracking type (which picks the set columns), equipment, primary and secondary muscles, notes, and an optional photo.
     - **Duplicate as variation:** start from a built-in exercise (e.g. "Bench Press, paused") and inherit its muscles and tracking type.
     - **Edit:** everything is editable. Changing the tracking type is blocked once sets exist that would lose data.
     - **Delete vs archive:** delete only while unused. Once the exercise has history, archiving hides it from pickers but keeps history, charts and PRs intact.
     - **Display:** a small "Custom" badge. Otherwise custom exercises behave exactly like built-ins in routines, charts, PRs, weekly muscle volume, export and sync.
   - **Adaptive shell and foldables:**
     - Window-size-class plumbing, and a navigation rail on wide screens.
     - List-detail scenes, starting with the exercise library and detail.
     - Content-width limits, and Fold 7 screenshot tests (cover and inner).
     - Done before the logger, so the logger is built adaptive from its first line, tabletop posture included.
4. **Routines and folders:** editor with drag-reorder, supersets, target sets, reps and rest, and starting a workout from a routine. Unlimited routines.
5. **Active logger:** this is the core of the app.
   - **Set rows:** columns adapt to the tracking type. Each row shows a set-type badge, the previous-session value (tap to copy) and the custom number pad.
   - **Rest timer:** ticking a set starts it and auto-advances to the next field, with separate warm-up and working durations.
   - **Supersets:** the cursor rotates through the group, and rest starts after the round.
   - **Editing:** add a set (copies the previous one), swipe to delete, replace an exercise, reorder, and add notes.
   - **Live workout notification** (added 2026-09-29 on request): one ongoing notification for the whole workout, from start to finish. On Android 16+ it is a promoted Live Update, so it also shows as the status-bar chip and in One UI 8's Now Bar on the lock screen. Older Android versions show a regular ongoing notification.
     - **During a set:** workout time, the current exercise and the next set, e.g. "Bankdrücken · Satz 3 von 4 · 80 kg × 8".
     - **During rest:** switches to the rest countdown with a progress bar (`ProgressStyle`). The system renders the countdown, so it keeps ticking in the background and on the lock screen without the app updating it every second.
     - **Actions:** complete set, +30 s, skip rest. A set can be logged without unlocking the phone. Tapping the notification opens the logger.
     - **End of rest:** an exact alarm fires the haptic and sound. If exact-alarm permission is denied, a short foreground service does it instead.
     - **Lifecycle:** it appears when a workout starts (or after a reboot mid-workout) and disappears on finish or discard. It reads the same Room state as the logger, so the app and the notification never disagree.
   - **Finish flow:** summary (duration, volume, sets, PRs), the option to update the routine with today's values, and cleanup of empty sets.
6. **History:** list and calendar, workout detail, edit past workouts, repeat a workout, save it as a routine.
7. **Progress:**
   - Per-exercise charts: e1RM, best weight, volume, reps.
   - PR board, weekly sets per muscle (with a body heatmap), and a consistency calendar with streaks.
   - Monthly report.
   - **Stall detection** (added 2026-09-30 on request): an exercise is stalled when its best e1RM hasn't improved
     for N weeks (default 3; Milestone 9 makes it a setting).
     - It is a pure `core/domain` function over the exercise's history.
     - Only exercises trained during the window count, so a break doesn't show up as a stall.
     - Exercises without an e1RM (bodyweight, timed) use their main record instead: most reps, longest duration.
     - Stalled exercises get a badge in Progress and on the exercise detail.
8. **Body:** bodyweight and measurements with charts, and progress photos (stored locally, compared side by side).
9. **Settings, import and export:**
   - Settings: units, rest defaults, gym profiles, the e1RM formula (Epley or Brzycki), the stall window, the RPE
     column toggle, theme, language (System / English / Deutsch).
   - **Gym profiles** (added 2026-09-30 on request; they replace a single plates-and-bar setting):
     - Each gym has its bars, plate inventory, dumbbell steps (e.g. 2 kg steps up to 40 kg, then 2.5 kg) and
       the stack step for machines and cables. One profile is active, and the logger can switch it.
     - **Load rounding:** one domain helper rounds a target weight to what the active gym can load, by equipment:
       plates for barbells and EZ bars, the dumbbell steps for dumbbells, the stack step for machines and cables.
     - **Plate calculator:** tapping a barbell weight in the logger shows the plates per side (`PlateCalculator`,
       built in Milestone 2).
     - **Warm-ups:** an "add warm-up sets" action generates the ramp (`WarmupGenerator`: bar, then about
       40/60/80 %) with weights rounded through the active profile. Dumbbell exercises skip the empty-bar step.
     - **Schema:** a synced `gym_profile` table, so a version bump, a migration and a migration test.
   - JSON full export and import (lossless) and a CSV export.
   - **Hevy and Strong CSV import**, with an exercise-matching review screen (fuzzy match, unmatched names become custom exercises) before anything is committed.
     - **Real Hevy format** (from a German-locale export; the user's real file stays in the gitignored `sample/`, so tests build synthetic fixtures in this format):
       - Columns: `title, start_time, end_time, description, exercise_title, superset_id, exercise_notes, set_index, set_type, weight_kg, reps, distance_km, duration_seconds, rpe`.
       - Dates are localized, e.g. `12 Sept. 2026, 18:05`, in local time with no offset. Parse both German and English month abbreviations (`Mai`, `Juni`, `Juli`, `Sept.`, `Okt.`, `Dez.`, …).
       - Weights use a dot decimal. Notes can span multiple lines, so the CSV parser must handle quoted newlines.
       - Cardio can be logged as timed warm-up sets (e.g. 300 s on a ski erg).
     - **Exercise names:**
       - Hevy's German built-in names, e.g. "Latzug (Kabel)" or "Beinbeugen sitzend". The seed carries these as German aliases.
       - Gym-specific custom variants, suffixed with a gym name, e.g. "Beinpresse Studio Nord" or "Latzug Studio Nord unten". These are the same movement on a different machine, with different weights.
     - **Matching rules:**
       - An exact or alias match is used directly.
       - Any other name becomes a custom exercise, a *variation of* the best match. It is never silently merged, because merging would mix weights from different machines.
       - The review screen lets the user merge where they want to.
       - If a source set has weights but the matched exercise's tracking type has no weight column, a weighted variant is picked instead, so no data is lost.

### Phase 2: Design and motion polish (added 2026-09-30 on request; moved up to follow Phase 1 on 2026-10-02)
The app works but feels a bit soulless. This phase gives it character: a little quirky and playful, with
animations that make logging feel good. It depends on no other phase, so it comes right after the MVP:
the app gets its character before sync, the server and the web GUI build on it.
- **Design audit:** go through every screen on the cover and inner displays, then settle the visual
  identity: palette, a display typeface with personality for big numbers and headings, shapes, and
  illustrated empty states instead of plain icons.
- **Voice:** playful microcopy for empty states, finishes and records, written in English and German
  alike (not translated word for word).
- **Motion:**
  - Ticking a set: the check pops and the row fills.
  - Numbers that change (volume, timers, the finish totals) roll like an odometer.
  - Records: a trophy burst or confetti, with a haptic.
  - The rest ring pulses as it runs out.
  - Shared-element transitions from lists to details, screen transitions, and predictive back.
  - Fold and unfold keep their place smoothly.
- **Haptics:** set completion, records, rest over.
- **Respect "remove animations":** everything falls back to simple fades when the system scale is off.

**How it runs** (planned 2026-10-02):
- **The look is chosen on the real app, not on mockups.** Every color, typeface, shape and the few component
  styles that differ go through `LioraTheme`, so a design is a set of tokens the app can switch between.
- **The user's direction:** calm, soft, rounded cards on tinted surfaces; color carries meaning (done, the one
  accent) but not per muscle group, which would get overwhelming; big round checks that fill in; clean bold
  type; floating pieces such as a pill nav bar and a timer pill. Playfulness lives in motion and copy more
  than in the look. Reference screenshots stay in the gitignored `samples/`.
- **Logging speed stays first:** no animation blocks or delays a tap, celebrations never hold the screen,
  and the logger keeps its density (previous values, kg, reps, the pad).
- **Parts:**
  - **2a, audit and identity:** a screenshot sweep of every screen, and a design lab of five directions
    (Lagoon, Paper, Tide, Ember, Volt) on the real app, plus three from colors the user picked (Indigo #340396,
    Cobalt #02519C, Violet #5504CF). The user picks or mixes; the winner becomes the
    theme, the others and the preview setting go, and the unused fonts leave the APK.
  - **2b, screens:** the identity on the most-used surfaces (logger rows, pad, rest timer, Train, finish sheet,
    Progress cards, body heatmap) and illustrated empty states; fixes from the audit (e.g. Progress stats
    that wrap in wider typefaces).
  - **2c, motion in the logging loop:** check pop and row fill, odometer numbers, rest ring pulse, haptics.
  - **2d, celebrations and navigation:** the record burst, list-to-detail transitions, predictive back,
    fold and unfold continuity.
  - **2e, voice:** the playful copy, in English and German written each on its own.
- **Testing motion:** screenshots at fixed points of an animation with the test clock paused, and a short
  device checklist at the end of 2c and 2d: only the phone tells whether it feels good.

### Phase 3: Sync and integrations
- `sync/gdrive` backend and the sync settings screen (connect, last sync, sync now).
  - One-time setup on your side: a Google Cloud project with the Drive API, a consent screen published to
    production (`drive.appdata` is non-sensitive, so there's no review; in testing mode access expires after
    7 days), and an Android OAuth client per build: `app.liora` with the release SHA-1, `app.liora.debug`
    with the debug SHA-1. The app holds no client id: Google matches the package and signing key.
- Health Connect:
  - Writes a finished workout as an `ExerciseSessionRecord` (strength training).
  - Reads `WeightRecord` and `BodyFatRecord` into Body.

**How it runs** (planned 2026-10-08):
- **One format for backups and sync.** A sync batch is a backup (`BackupFormat.kt`) holding the rows changed
  since the last push, each with its parents so it nests the same way, and deletions as rows with a
  `deletedAt` (a new optional field, so format version 1 stays). Restoring a backup and merging a batch are
  the same merge: per row, the newer HLC wins. A synced table that goes into backups syncs with them.
- **What syncs** is what a backup holds, plus deletions. The workout in progress stays on its device until
  it's finished, and so does device state in `local_meta` (the rest timer, the gym in use).
- **A sync** (`SyncEngine` in `core/data/sync`, one at a time):
  - Pull first: other devices' batches since the cursor, merged in one transaction that also stores the new
    cursor, with photos' images fetched before. Rows that arrive this way aren't dirty: the store has them.
  - Then push this device's dirty rows as one batch, uploading the images of photos it hasn't sent yet.
  - Then clear `dirty` on the rows pushed, unless they changed meanwhile (their HLC moved on).
- **The clock survives restarts.** The highest HLC merged from elsewhere is kept in `local_meta`, and the
  clock starts past it, so an edit made after a restart beats the version it replaces even when this
  phone's clock is behind.
- **`SyncBackend`** (`core/data/sync`): push a batch, pull batches since a cursor, put and get blobs. The
  cursor belongs to the backend (Drive's per-device sequence numbers; the server's sequence in Phase 5).
- **Google Drive** (`sync/gdrive`, Android):
  - The app data folder: hidden from the user's Drive, counted against their quota.
  - `AuthorizationClient` for the `drive.appdata` scope, and REST v3 through the Ktor client.
  - Files: `changes-<deviceId>-<seq>.json.gz`, each written only by its device, so there is no contention;
    the photos as `blob-<photoId>.jpg`.
  - Compaction: a device with more than 100 batches writes everything it has (deletions too) as
    `snapshot-<deviceId>-<seq>.json.gz` and deletes its batches up to there. A device whose cursor is behind
    reads the snapshot instead; merging the same rows twice changes nothing.
- **When:** WorkManager, with network: after a workout finishes, when Liora goes to the background with
  changes not yet synced, twice a day, and on "Sync now". Failures retry with backoff; nothing waits on sync.
- **Settings › Sync:** connect Google Drive (consent once), the account, when it last synced (or what went
  wrong), Sync now, and Disconnect (stops syncing and revokes access; the data stays here and in Drive).
  Connecting marks every row dirty and resets the cursor, so a new store gets everything.
- **Health Connect** (`integration/healthconnect`, Android): Settings › Health Connect asks for its
  permissions. Finished workouts are written as `ExerciseSessionRecord`s (strength training: name, start, end,
  notes), keyed by the workout's id (`clientRecordId`), so an edit replaces one and a deletion removes it.
  Weight and body fat are read into Body, one value per type and day, Liora's own entries winning; records
  Liora wrote itself are skipped.
- **Parts:**
  - **3a, the sync engine:** `deletedAt` in the format, choosing a batch's rows, the shared merge, the
    persisted clock, `SyncEngine` and `SyncBackend` with photos' images, tested with two phones and an
    in-memory backend. Needs no Google setup.
  - **3b, Google Drive:** sign-in, the Drive backend with compaction, WorkManager, Settings › Sync.
  - **3c, Health Connect.**
  - **3d, first launch** (added 2026-10-08 on request): a short walkthrough on the first start, showing how
    Liora works (routines, the logger, progress) and ending with an offer to set up Google Drive sync.

**3d, first launch** (spec and build 2026-10-09). No schema change.
- **What it is:** five pages, swiped or stepped through with Next, shown once per device in place of the tabs
  (no rail, no bottom bar). Dots show the page. Skip, on pages 1–4, jumps to the last page, because its
  offers are choices rather than tour. Back goes to the page before; on the first page it leaves Liora as
  usual, and the walkthrough comes back next time.
  1. **Welcome** (a new `Illustration.Welcome` scene): what Liora is in one line, and "Kilograms or pounds?"
     as a two-way switch, the current setting selected (kg on a new install). Tapping one writes it at once
     through `SettingsRepository.update`, so weight, distance and body measurements follow together (lb with
     miles and yards, and inches); the details stay in Settings. Asked first so the demo on page 3 shows the right unit.
     Leaving kg as it is writes nothing, so a second phone that connects to Drive on page 5 takes the units
     from the other phone.
  2. **Routines** (`Illustration.Routines`): plan workouts once as routines, from the exercise catalog, with
     folders and supersets; or start an empty workout and add as you go.
  3. **Logging** (`Illustration.Workout` above a demo): one set row as the logger draws it, with last time's
     values in the Previous column and round numbers in the chosen unit (60 kg or 135 lb × 8). Tapping its
     check ticks it for real: the check pops, the done tint spreads across the row, and `LioraHaptics.setLogged`
     fires. Tapping again reopens it. The text says the rest timer starts by itself and the workout stays in
     the notification. For this, the check and the row fill moved from the logger to `designsystem/motion`
     (`SetCheck`, `drawSetFill`), so both draw the same.
  4. **Progress** (`Illustration.Chart`): records as they happen, a chart for every lift, the weekly streak
     and the monthly report.
  5. **Your data** (`Illustration.Sync`): Google Drive sync, the same as Settings › Sync: Connect (tonal, so
     "Let’s go" stays the main way on) asks through `LocalCloudConsent`, then `SyncRepository.connect`. The
     page shows the account once `SyncState.account` has it (reacting to the data, not to the call). Declined changes nothing; Failed says it can be set up
     later in Settings › Your data. One line covers a second phone: connect the same account and the
     workouts come over. Below that, a quiet "Import from Hevy or Strong" opens the file picker
     (`rememberFileOpener`); a picked file finishes the walkthrough and opens `ImportRoute(source)` over what
     was underneath (the Train tab on a first start), so back from the review lands there. "Let’s go" finishes.
- **Copy:** in the voice of the empty states, light and warm, and still saying what to do; what sync does is
  explained plainly. Drafts: "Welcome to Liora", "Plan it once", "One tap per set", "Watch it add up",
  "Keep it safe", "Let’s go". German is written on its own, not translated.
- **Who sees it:** `WalkthroughRepository` (`core/data/walkthrough`) keeps a `walkthrough` key in `local_meta`,
  `pending` or `done`, so each phone decides for itself (a second phone needs page 5 most).
  - `settle()` decides once when the key is missing: an install that already has a seed version
    (`exercise_seed_version`, so it ran an earlier Liora) is `done`, anything else `pending`. It returns
    whether the walkthrough is pending.
  - `AppStartup` settles before seeding, and `MainActivity` settles up front (like the settings), so the first
    frame is the walkthrough or the tabs, with no flash. Whichever runs first decides. The seed version is only
    written after `AppStartup` has settled, so a new install can't pass for an old one. Existing installs,
    the phone with real data among them, never see it.
  - `finish()` writes `done`; `showAgain()` writes `pending`.
- **Where it lives:** `feature/walkthrough` (`WalkthroughScreen`, `WalkthroughViewModel`, `walkthroughModule`).
  It isn't a route: `LioraApp` shows it in place of the shell while `pending` is true, and the shell comes
  back when `finish()` lands, so the screen closes in reaction to the data. The navigator's back stack is
  kept underneath. The page survives folding (the pager's state is saveable) and Android ending the process.
  The import link hands its source to `LioraApp`, which navigates. `LioraApp` holds the screens' saved state
  (the entry decorators) above the walkthrough, so a screen comes back as it was, scrolled where it was.
- **Seeing it again:** Settings gets a last section, About Liora, with "How Liora works", which calls
  `showAgain()`. Finishing it returns to Settings. It shows the current units and sync state, and changes nothing unless something is
  tapped. It is also how the walkthrough gets its device check, since the test phone already has data.
- **Layouts:** on the cover screen, a scrolling column: illustration, title, text, the page's control, and the
  dots with Next at the bottom. On the inner screen, the illustration sits beside the text, centred, capped
  at about 840 dp. Only drawing moves: the pager follows the finger, the current dot widens, and nothing
  advances or loops on its own.
- **Accessibility:** each page reads as its heading and text; the dots read as "Page 2 of 5"; the demo
  check has the logger's description.
- **Tests:**
  - `core/data` (jvmTest): a new database settles as pending and an existing one (seed version, no key) as
    done; settling again changes nothing; `AppStartup` settles before seeding, so a new database is still
    pending after it runs; `finish()` and `showAgain()`.
  - `TestLioraApplication` marks the walkthrough done before each test, so existing tests open on the tabs.
    The walkthrough's tests opt in.
  - `WalkthroughFlowTest` (Robolectric): a new install shows the walkthrough, not the tabs; Next and swiping
    step through it; Skip jumps to page 5; Pounds writes the units and the demo row shows pounds;
    ticking the demo check; Connect with the test consent shows the account and turns sync on; "Let’s
    go" opens the tabs, and the walkthrough doesn't come back after the activity is recreated;
    Settings › How Liora works shows it again, and finishing returns to Settings.
  - Screenshots of every page on the cover screen, two on the inner screen, light, German, and the new scene
    in `IllustrationsTest`.
- **Not in 3d:** Health Connect (Settings has it), the notification permission (still asked as the first
  workout starts), the language (the phone's, or Settings on Android 13+), gym setup (standard equipment
  until one is set up) and starter routines (with programs in Phase 4).

### Phase 4: Programs, progression, and extras (moved up 2026-10-09 on request)
- Progression and rotations (decided 2026-10-09: double progression, and a folder of routines as a rotation;
  linear, 5/3/1 and GZCLP aren't planned, though the engine leaves room for more rules).
  - **Progression rules** (added 2026-09-30 on request): a pure `core/domain` function takes a rule and the recent
    history and returns the next session's targets (weight and reps per set) plus the reason, e.g. "8 reps on
    all 3 sets at 80 kg last time, so +2.5 kg". Target weights go through the active gym profile's load rounding.
  - The reason is structured (a reason type plus its values), not an English sentence, so the UI renders it in
    English or German with locale-aware numbers.
  - **Safety bound:** a target too far above the recent best is flagged before it is shown (defined below). The
    same check guards AI suggestions in Phase 6.
- Home-screen widget, and the workout on the lock screen instead of Wear OS (4d below).
- Updates from GitHub: the release app checks for a newer version, downloads it and installs it (4e below).

**How it works** (spec 2026-10-09, worked out with the user):
- **Who it's for first:** the main user (see Context) trains a rotation of several routines, mostly 3–4
  working sets of 8–20 reps on machines, cables and dumbbells, with a good share of exercises in more than
  one routine, and a set of routines per gym.
- **Parts,** each committed on its own:
  - **4a, double progression** on a routine's exercises: the engine, the routine editor, targets in the logger.
  - **4b, rotations:** a folder of routines that take turns, with what's next on the Train tab.
  - **4c, the home-screen widget.**
  - **4d, the lock screen:** the workout as an Android 16 Live Update, instead of Wear OS.
  - **4e, updates from GitHub** (added 2026-10-09 on request).
- **4a, double progression:**
  - **The rule** is set per exercise of a routine (`ProgressionRule` in `core/model`, one kind for now:
    double progression). Without it, an exercise works as today: last time's values, and "Update the
    routine" at the end. It's offered for exercises counted in weight and reps (`WeightReps`,
    `WeightedBodyweight`); each working set needs a rep range (say 8–12), and the editor asks for one.
  - **Last time** is the exercise's latest session in a workout started from this routine, so a lighter
    day in another routine stays its own thing. Without one yet (a new rule, or history imported from Hevy
    or Strong, which isn't tied to routines), it's the exercise's latest session anywhere.
  - **Next targets,** set by set (each planned working set against its counterpart last time, as the
    Previous column pairs them; warm-ups and drop sets don't count):
    - **Every working set reached the top of its range:** each goes up a step from the weight lifted, and
      the reps start again at the bottom.
    - **Two sessions in a row with a set below the bottom:** each goes down a step, and climbs again from
      there. One bad day changes nothing.
    - **Otherwise:** the same weights, each set aiming for last time's reps and the weakest (fewest reps,
      the first on a tie) for one more: a rep more in all, so 8, 8, 8 becomes 9, 8, 8 (changed 2026-10-09
      on request, from a rep more on every set).
    - A set skipped last time means not every set reached the top: no step up.
  - **A step:** the next weight the gym can load for the exercise's equipment (`LoadRounding.step`: the
    rack for dumbbells, plates for bars, the stack step for machines and cables). A machine or cable can
    have its own step (`exercise_settings`, set on the exercise's page), added to the weight lifted, so a
    stack starting at 7 kg goes 7, 12, 17. The logger's ± uses it too.
  - **The engine** (`Progression.next` in `core/domain`, pure): the rule, the planned sets, the sessions,
    the gym and the equipment go in; each working set's target (weight, and the reps aimed for) and a
    reason come out. The reason is structured (`ProgressionReason`: first time, top reached, keep going,
    below the range twice), so the UI says it in English or German with the locale's numbers: "12 reps on
    all 3 sets at 80 kg last time, so 82.5 kg".
  - **Safety bound:** a target is flagged when it is more than 10 % above the heaviest weight lifted in the
    last 6 weeks and more than one gym step above it (Settings › Progress can change the 10 %). By weight,
    not e1RM, since the reps drop as the weight goes up; and one step is never flagged, as a step on a light
    cable is already 30 % or more. It catches a typo in an exercise's own step or a stray weight in the
    history. It never blocks.
  - **In the logger:** starting the routine fills each set's weight with its target, and the reps aimed for
    are the placeholder, logged with one tap like last time's values are now. Under the exercise's name, the
    reason, with an arrow when the weight goes up or down. Routine detail shows each exercise's next
    targets. "Update the routine" at the end leaves exercises with a rule alone, since their targets come
    from the history.
  - **Stored:** schema v4 (`AutoMigration` 3→4, with a migration test) adds `routine_exercise.progression`
    (the rule, null for none), `exercise_settings.load_step_kg`, and `workout_set.aim_reps` (the reps a
    planned set aims for, which come before last time's as its placeholder, so the notification's "log set"
    logs them too). All three are optional fields in the backup format (no version bump), so they sync.
    Targets are worked out when the routine starts; the reasons are worked out again where they're shown,
    and never stored.
  - **Tests:** the engine in `core/domain` (top reached, keep going, below twice, a skipped set, a first
    session, the fallback to any routine; kg and lb gyms; racks, plates, stacks and an exercise's own step;
    the safety bound), the migration and the backup round trip, and the editor and logger in Robolectric
    with screenshots at cover and inner sizes.
  - **Settings › Progression** (added 2026-10-09 on request, after trying it on the phone): the choices above
    become the defaults of `ProgressionSettings`, each a synced preference row (`progression.*`), so they
    follow the user from phone to phone. Reps to aim for (a rep more on the weakest set, on the first set
    below the top, the same as last time, or on every set); a step heavier when every set reaches the top,
    the sets add up to the top, or the first set reaches it; below the range, step down after two sessions
    in a row, right away, or never; last time means the routine's own last session or the latest anywhere.
    The jump limit moved here from Progress. Each choice shows an example in its dialog, and the reasons
    in the logger and on routine detail follow whichever is chosen.
- **4b, rotations:**
  - A folder can be made a rotation (`routine_folder.rotation`, in the same schema v4). Its routines take
    turns in their order: next up is the one after the routine of the latest workout started from the
    folder, or the first.
  - The Train tab heads with "Next up" and Start, for the rotation trained last; other rotations mark their
    next routine in the folder. Starting any routine of a rotation moves it on from that one, so skipping a
    day is just starting another.
  - One rotation per gym works naturally, as a gym's routines sit in their own folder. No blocks or
    deloads for now.
  - As built: `Rotations` (`core/domain`) works out a folder's turn; `RotationRepository` (`core/data`) gives
    each rotation's turn from the folders, routines and finished workouts, the one trained last first. A
    workout counts for a routine when it was started from it, or, for history imported from another app,
    when it has the routine's name. The folder's menu makes it a rotation or stops it; its header shows the
    turning arrows. The Next up card shares a list item with Quick start (whose button turns tonal beside
    it), so one that appears later, as a folder is made a rotation, isn't scrolled away above the top.
- **4c, the widget** (Jetpack Glance): next up in the rotation with Start, and the streak; during a workout,
  its name, time, sets done and the rest countdown, opening the logger. In the app's look, light and dark.
  - **One widget, three states** (`app/android/.../widget/`, Glance 1.2):
    - **Training:** the workout's name, the time since it started, the sets done, and while resting the
      time left. The clocks are `Chronometer`s (through `AndroidRemoteViews`), so they tick by themselves
      without the app redrawing the widget every second. Tapping it opens the logger.
    - **A rotation:** "Next up in Push Pull Legs", the routine, and Start, which starts it and opens the
      logger. With several rotations, the one trained last, as on the Train tab.
    - **Otherwise:** Quick start, with Start empty workout.
    - Under the idle states, the weekly streak when there is one ("3 weeks in a row").
  - **Sizes:** resizable; at two cells wide only the main line and its button or clock.
  - **Look:** Liora's violet color schemes, light and dark with the phone, or the wallpaper's colors when
    that setting is on (`glance-material3`). The system font, since widgets can't load Sora; the corners
    follow the launcher's.
  - **Keeping it current:** `WidgetUpdater`, started with the process like the workout notification, watches
    the workout, the rest timer, the rotations and when workouts were trained, and redraws the widget when
    what it shows changes. The clocks tick on their own.
  - **Starting from it:** Start opens `MainActivity` with the routine (or nothing, for an empty workout);
    the activity starts the workout in the process's scope, so it finishes even if the activity goes, then
    opens the logger. A workout already running opens instead (the widget shows it anyway, so only a race
    gets there).
  - **Tests:** the widget's state from the repositories in each case; the widget's content in each state
    with `glance-appwidget-testing`; and screenshots of the three states.
- **4d, the lock screen** (decided 2026-10-09 instead of Wear OS: with wrist straps on, a watch is hard to
  use mid-set; a watch app stays a maybe for later): the live workout notification becomes an Android 16
  Live Update (a promoted ongoing notification), with the rest countdown as a chip in the status bar and
  large on the lock screen, readable without unlocking. On the Fold, check whether Samsung's Now Bar shows
  it too. Below Android 16 the notification stays as it is. Together with the end-of-rest sound in
  headphones, which is already there, rest needs no hands.
  - **Most of it was there already:** the workout notification asks to be promoted
    (`setRequestPromotedOngoing`), draws the rest with `ProgressStyle`, counts it down in its header, and
    gives the chip the set ("2/4") between sets and the countdown while resting.
  - **Android's rules** (read in AOSP, 2026-10-10): Live Updates shipped in Android 16 QPR1. A notification
    is promoted when it asks to be, is ongoing with a title, uses no style or big text, call or progress, isn't
    a group summary, draws no views of its own, and is **not** colorized; the user can still turn Live
    Updates off per app. The first Android 16 release had other rules (colorized only), and Robolectric's
    Android 16 is that release, so `WorkoutNotificationTest` checks the shipping rules itself.
  - **On the lock screen without unlocking:** the workout notification and the end-of-rest heads-up are
    public (`VISIBILITY_PUBLIC`), so a lock screen that hides what notifications say still shows the set and
    the rest. Nothing in them is private.
  - **On the Fold** (checked 2026-10-10, Android 16 QPR2, One UI 8.5): Android promotes it
    (`dumpsys notification` shows `PROMOTED_ONGOING` and `vis=PUBLIC`). The status bar chip, in violet
    with the logo, says "1/4" between sets and counts the rest down ("01:15"; Android pads the minutes). On
    the lock screen, Samsung's Now Bar shows it without unlocking: "Pause 0:51" and what's next, a long
    exercise name cutting off the values. Left as it is, on the user's call: no Settings row for Live
    Updates and no reordered line.
- **4e, updates from GitHub** (added 2026-10-09 on request; worked out with the user 2026-10-10): Liora is
  sideloaded, so nothing tells it about a new version. The release app checks this repo's GitHub Releases,
  says when one is newer than what's installed, with what's new, and on a tap downloads it and installs it.
  Obtainium already does this for any app on GitHub; a built-in one fits Liora's own notes, voice and look.
  - **Decided with the user:** releases are built and signed on the dev machine (the key never leaves
    `~/.liora`); Liora asks before downloading anything; stable releases only, no beta channel; and the
    user moves from the debug app to the release app (a backup carries the data over), since only the
    release app updates itself.
  - **Versions:** one place, `version.properties` at the root (`version=0.4.0`); the version code follows
    from it (major × 10000 + minor × 100 + patch), so it only grows. The first release is 0.4.0, Phase 4.
  - **Releasing** (`node tools/release/release.mjs`): on a clean, pushed `main`, it runs `check`, builds the
    signed release APK, checks its signature with `apksigner`, tags `v0.4.0`, and publishes a GitHub
    Release with `liora-0.4.0.apk` and the notes from `docs/releases/0.4.0.md`. `--dry-run` stops before
    tagging. GitHub gives each file a SHA-256 (`digest`), so there's no checksum file.
  - **Release notes** are written for people, in English and German: `## English` and `## Deutsch`
    sections, of which Liora shows the one in its language (English otherwise).
  - **Checking:** GitHub's REST API (`releases/latest`, which leaves out drafts and pre-releases; no token,
    60 requests an hour) through Ktor in a new `integration/github` module, tested with `MockEngine`. At
    most once a day when Liora comes to the front (an hour after a failed try), while "Look for updates" is
    on (Settings, on by default, per phone), and whenever asked from Settings. What it found is kept in
    `local_meta`, so it survives a restart.
  - **Telling the user:** a card (`UpdateCard` in `core:ui`) at the top of the Train tab, "Liora 0.5.0 is
    here", with the first lines of what's new, Update and Not now (which hides it for that version; Settings still offers it). Settings ›
    About Liora shows the version installed and where updates stand: up to date (checked when), a newer
    one with what's new and Update, or GitHub out of reach. Debug builds show their version and say they
    don't update themselves.
  - **Downloading:** after Update, in the background (WorkManager) into app-private storage, with its
    progress on the card and in Settings. Then checked: the size and SHA-256 match, it's Liora, a higher
    version, and signed with the same key as the installed app (`PackageManager`), so nothing else can
    pass for an update.
  - **Installing:** through `PackageInstaller` (`REQUEST_INSTALL_PACKAGES`), started from the screen, so
    Android's confirmation can show. The first time, Android asks to allow installs from Liora. Once Liora
    has installed itself, Android 12 and later can update it without that dialog
    (`USER_ACTION_NOT_REQUIRED`). Same package and key, so the data stays and Room migrates as usual.
    Never during a workout: Update waits until it's finished. The APK is deleted once it's installed.
  - **Tests:** versions and notes (domain), the GitHub client (`MockEngine`), the repository's rules (once
    a day, Not now, a newer version showing again), the APK checks, and the card and Settings in UI tests
    with screenshots, the GitHub side faked. The device check is a real update: 0.4.0 installed from
    GitHub, then 0.4.1 published and installed from Liora.

### Phase 5: Self-hosted server and web GUI (moved back 2026-10-09 on request: not a priority now)
- `server/`: Ktor, PostgreSQL, Exposed, Flyway, Argon2 passwords, JWT access tokens plus refresh tokens per device.
- `core/api-contract` DTOs, the `/sync` endpoints, a blob store on a filesystem volume, and a read-only `/api/v1` REST API with OpenAPI docs.
- `docker-compose.yml` with the server and Postgres.
- `sync/server` client backend, and moving from Drive to the server from the app.
- `app/web`: Compose Multiplatform (wasmJs) reusing `designsystem`, with desktop layouts. It is served by the Ktor server and offers a history browser, dashboards, a routine editor, and import/export.
  - Re-check Compose Web maturity before starting: it is Beta as of Compose Multiplatform 1.12.

### Phase 6: AI (opt-in; runs on the Phase 5 server, so API keys never sit on the phone)
- Claude API with tool use over read-only domain queries (`get_workouts`, `get_exercise_history`, `get_prs`, `propose_routine`). Features:
  - "Ask your log" (e.g. "how has my bench moved since March?").
  - Post-workout and weekly insights.
  - Natural-language or voice logging into the active workout.
  - Routine and program generation that respects your equipment (the gym profiles) and history.
  - Plateau and deload suggestions on top of the deterministic engine (stall detection and progression rules).
  - Every suggested weight passes load rounding and the safety bound, like the engine's own targets.
- Optionally, an **MCP server** endpoint so Claude Desktop or other clients can query your own data.

---

## Verification

**Automated** (`./gradlew check`: unit tests, lint, detekt, Spotless):
- **Domain:** golden tests for e1RM, PR detection, set-per-muscle math, plate and warm-up rounding, load rounding per gym profile, stall detection, progression rules and the safety bound, unit conversion. Parsers are tested against fixture CSVs in Hevy and Strong export format.
- **Sync:** a property test runs two or three simulated replicas with random edits, deletes and pushes against an in-memory backend and asserts they converge. The Drive layout is tested against a fake Drive.
- **Database:** DAO tests on the JVM with the bundled SQLite driver, plus Room migration tests from exported schemas.
- **UI:** Compose UI tests for the logger critical path, and Roborazzi screenshot tests for designsystem components in light and dark.
- **Localization:**
  - The missing-translation check compares every `values/` key against `values-de/`.
  - Screenshot tests also render key screens in German, which runs about 30% longer, to catch truncation.
  - Unit tests cover decimal-comma parsing and formatting, and umlaut-insensitive exercise search.
- **Foldables:** Roborazzi screenshots of key screens at the Fold 7 cover size (~411 × 960 dp) and inner size (~984 × 1092 dp), plus inner landscape.
  - Tests assert the two-pane layouts and the navigation rail appear on the inner screen and not on the cover screen.
- **Custom exercises:**
  - Create, edit, archive and delete rules, including the blocked tracking-type change once sets exist.
  - A custom exercise shows up in PRs, charts and weekly muscle volume like a built-in.

**Manual on a device** (`./gradlew :app:android:installDebug`):
1. Create a routine with a superset.
2. Start it and log sets with the number pad. The previous values appear.
   - Lock the phone mid-workout: the live notification shows the next set, the rest countdown runs in the Now Bar, and "complete set" on the lock screen logs the set, which then shows up in the app.
3. Force-stop the app mid-workout and reopen it. The workout resumes intact.
4. Finish the workout. The PR banner and summary appear, and the routine-update prompt works.
5. Check History, Progress charts and Body entries.
6. Export JSON, reinstall, import it, and confirm the data is identical.
7. Import a real Hevy or Strong CSV and review the matched exercises.
8. Mid-workout, search for an exercise that doesn't exist, create it from the picker, log sets, finish, and see it in Progress.
9. Switch the app to Deutsch. All screens are translated, weights show "82,5 kg", and searching "Kreuzheben" finds deadlifts.
10. **On the Galaxy Z Fold 7**, or the Android Studio resizable or foldable emulator:
    - Start a workout on the cover screen, unfold mid-set, and check that the logger switches to two panes with the timer and typed values intact.
    - Fold back.
    - Stand the phone half-open in tabletop posture and check the logger splits across the hinge.
11. **Phase 3:** use two devices on the same Google account, edit on each, sync, and confirm they converge. The workout also shows up in the Health Connect app.
