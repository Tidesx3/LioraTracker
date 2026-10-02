# Liora: workout tracker ecosystem plan

The full plan, agreed on 2026-09-29. README.md has the short roadmap; this file has the specs for each milestone.
Check it when a milestone or phase starts, and keep **Progress** below current when one lands.

## Progress

As of 2026-10-02:

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
    and then a photo swaps it. Photos sync as blobs in Phase 2; until then `blob_id` stays empty.
- **Milestone 9 (settings, import and export)** is in progress, split into five parts, each committed on its own:
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
  - **9c (done): gym profiles.** `GymProfile` in `core/model`: a name, the unit its equipment is labelled in,
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
  - **9d (done): backup, restore and the CSV export.** Settings has a "Your data" section with three rows,
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
    - **Export workouts as CSV** (`WorkoutCsv` in `core/domain`): one row per logged set, RFC 4180 with dot
      decimals, weights and distances in the user's units named in the header, local times, exercise names
      in the app's language.
    - The platform side is `ExportFiles` (`AndroidExportFiles`: ZIP archives through the content resolver).
      Backups and restores finish even when Settings closes mid-way.
  - **Next: 9e**, Hevy and Strong CSV import with the matching review screen; then Milestone 9 and Phase 1
    are done.
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
  integration/healthconnect/ Android: write sessions, read bodyweight (Phase 2)
  sync/gdrive/               Android: Drive appDataFolder backend (Phase 2)
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
  - **Google Drive backend (Phase 2):**
    - Uses the `drive.appdata` scope: a hidden per-app folder that counts against the user's own quota.
    - Auth runs through Credential Manager and `AuthorizationClient`. The legacy GoogleSignIn API was removed in play-services-auth 22.
    - Calls the REST v3 API through the Ktor client.
    - Layout: each device only appends its own `changes/<deviceId>/<seq>.json.gz` batches, so there is no write contention. Any device can occasionally compact everything into `snapshot-<hlc>.json.gz`. Photos are stored under `blobs/`.
  - **Server backend (Phase 3):** `GET /sync/pull?cursor=` and `POST /sync/push` using the same ChangeSet DTOs. The server assigns a monotonic `serverSeq` that clients use as their cursor.
- Sync is triggered by WorkManager: when a workout finishes, periodically, and manually. It requires network.
- Switching backends means a full push of the local DB.
- **Manual backup** (JSON export and import via SAF) always works, independent of sync.

---

## Roadmap

**Phase 0 and Phase 1 are executed milestone by milestone.** Each milestone is built, tested and committed before the next starts. Phases 2–5 each get a short planning pass when we reach them.

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

### Phase 2: Sync and integrations
- `sync/gdrive` backend and the sync settings screen (connect, last sync, sync now).
  - One-time setup on your side: a Google Cloud project with an Android OAuth client (debug + release SHA-1). Testing mode is enough for personal use.
- Health Connect:
  - Writes a finished workout as an `ExerciseSessionRecord` (strength training).
  - Reads `WeightRecord` and `BodyFatRecord` into Body.

### Phase 3: Self-hosted server
- `server/`: Ktor, PostgreSQL, Exposed, Flyway, Argon2 passwords, JWT access tokens plus refresh tokens per device.
- `core/api-contract` DTOs, the `/sync` endpoints, a blob store on a filesystem volume, and a read-only `/api/v1` REST API with OpenAPI docs.
- `docker-compose.yml` with the server and Postgres.
- `sync/server` client backend, and moving from Drive to the server from the app.

### Phase 4: Web GUI, programs, extras
- `app/web`: Compose Multiplatform (wasmJs) reusing `designsystem`, with desktop layouts. It is served by the Ktor server and offers a history browser, dashboards, a routine editor, and import/export.
  - Re-check Compose Web maturity before starting: it is Beta as of Compose Multiplatform 1.12.
- Programs and progression engine: multi-week blocks, and rules like double progression or linear progression with deload (5/3/1, GZCLP).
  - **Progression rules** (added 2026-09-30 on request): a pure `core/domain` function takes a rule and the recent
    history and returns the next session's targets (weight and reps per set) plus the reason, e.g. "8 reps on
    all 3 sets at 80 kg last time, so +2.5 kg". Target weights go through the active gym profile's load rounding.
  - The reason is structured (a reason type plus its values), not an English sentence, so the UI renders it in
    English or German with locale-aware numbers.
  - **Safety bound:** a target more than X % (default 10 %) above the recent best (by e1RM, over the last few
    weeks) is flagged before it is shown. The same check guards AI suggestions in Phase 5.
- Wear OS companion, home-screen widget.

### Phase 5: AI (opt-in; runs server-side, so API keys never sit on the phone)
- Claude API with tool use over read-only domain queries (`get_workouts`, `get_exercise_history`, `get_prs`, `propose_routine`). Features:
  - "Ask your log" (e.g. "how has my bench moved since March?").
  - Post-workout and weekly insights.
  - Natural-language or voice logging into the active workout.
  - Routine and program generation that respects your equipment (the gym profiles) and history.
  - Plateau and deload suggestions on top of the deterministic engine (stall detection and progression rules).
  - Every suggested weight passes load rounding and the safety bound, like the engine's own targets.
- Optionally, an **MCP server** endpoint so Claude Desktop or other clients can query your own data.

### Phase 6: Design and motion polish (low priority; added 2026-09-30 on request)
The app works but feels a bit soulless. This phase gives it character: a little quirky and playful, with
animations that make logging feel good. It depends on no other phase, so it can move earlier at any time.
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
11. **Phase 2:** use two devices on the same Google account, edit on each, sync, and confirm they converge. The workout also shows up in the Health Connect app.
