# Liora: notes for Claude

A workout tracker (Android first, KMP) for personal use and self-hosting. See README.md for the product
summary and roadmap, and TODO.md for open bugs and polish.

**Where work stands:** `docs/PLAN.md` has the full plan, the specs for every milestone, and a **Progress**
section saying what's done and what's next. Read it before starting a milestone, follow its spec, and
update Progress (and README's status line) in the commit that finishes one.

## Commands

The build needs JDK 21 and an Android SDK (CI shows the minimal setup: `.github/workflows/ci.yml`).
On the Windows dev machine the JDK isn't on PATH in every shell. Export it first when needed:
`export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`

- `./gradlew check`: all tests plus Spotless, detekt and Android Lint. Run it before committing.
- `./gradlew spotlessApply`: formats code (ktlint_official style plus compose-rules).
- `./gradlew :app:android:assembleDebug` or `installDebug`.
- `./gradlew :app:android:installRelease`: R8-shrunk and signed with the key in the gitignored `keystore.properties` (README › Release builds); unsigned without it. Judge speed on this build, never on debug.
- `node tools/release/release.mjs [--dry-run]`: publishes a GitHub release of the version in `version.properties`, with the notes in `docs/releases/<version>.md` (README › Publishing a release). Publishing is public: only when the user asks.
- `./gradlew :app:android:recordRoborazziDebug`: re-renders screenshots to `app/android/src/test/screenshots/`. Read the PNGs to review UI changes.
- `java tools/icons/MakeIcons.java logo.png app/android/src/main/res build/icon-preview.png`: rebuilds the launcher and notification icons from `logo.png` (light artwork on black) and writes a preview sheet to check them by eye.
- `java tools/sound/MakeSounds.java app/android/src/main/res/raw build/sound-preview.png`: rebuilds the end-of-rest sounds (synthesized, no samples) and writes their waveforms to check for clipping and clicks.
- `node tools/routine/liora-routine.mjs <format|search|show|check|profile|mcp>`: the routine tool for AIs (tools/routine/README.md); `node --test tools/routine/test/routine.test.mjs` tests it (CI runs it too).
- **Making a routine for the user** (not changing code): use the `liora-routine` skill (`.claude/skills/liora-routine`).

## Architecture rules

- **Offline-first.** The UI reads Flows from repositories, and repositories write through to the local DB (Room, from Phase 1).
- **Dependencies:** `feature/*` depends only on `core/*`. Features never depend on each other. Cross-feature navigation goes through route keys in `core/navigation/Routes.kt` and the `Navigator`. `app/android` wires everything together (Koin modules, `entryProvider`).
- **Feature pattern:**
  - Each screen is a stateful `XScreen(viewModel = koinViewModel())` wrapping a stateless private `XContent(uiState, callbacks)`.
  - `XNavigation.kt` exposes `fun EntryProviderScope<NavKey>.xEntries(navigator)` and a Koin `xModule`.
  - To add a feature: register its entries in `LioraApp.kt` and its module in `LioraApplication.kt`.
  - A new route in `Routes.kt` also goes into `routes` in `SavedNavigation.kt`: the back stack is saved with the activity, so screens survive Android ending the process.
  - **List-detail:** tag a list entry with `metadata = ListDetail.listPane { placeholder }` and what it opens with `ListDetail.detailPane()`. Open items with `navigator.openFromList(listRoute, route)`, so the detail pane shows one item and back returns to the list. `LocalPaneRole` tells a screen whether it sits beside its list (no back arrow, highlight the selection).
  - **Results across features:** a screen that hands a value back (e.g. `ExercisePickerRoute`) takes a request key in its route and closes with `navigator.goBackWithResult(key, value)`. The caller's entry collects it with `NavigationResultEffect(navigator.results, key)`, which also delivers a result that arrived while the caller was off screen.
- **The workout in progress:** `ActiveWorkoutRepository` (start, finish), `WorkoutEditor` (exercises) and `SetLogger` (sets, ticking off) in `core/data` serve both the logger and the live workout notification in `app/android/.../workout/`. Both read the same Room state, so they never disagree. The logging rules (next set, superset rounds, rest, placeholders) live in `core/domain/WorkoutProgress.kt`.
  - **Progression:** a routine's exercise can have a rule (`ProgressionRule`; the engine is `Progression` in `core/domain`). `ProgressionRepository` works out the targets from the exercise's last sessions in that routine; starting the routine fills them into the planned sets (weight, and `aimReps`, the placeholder before last time's). The reasons are worked out again where they're shown (`ProgressionNote` in `core:ui`) and never stored.
  - **Past workouts** are corrected with the same tools: `WorkoutHistoryRepository.edit(id)` returns a `FinishedWorkoutSession` (a `WorkoutSession`, like `ActiveWorkoutRepository`) with its own editor and set logger. The logger screen runs on either (`LoggerViewModel` with or without a finished workout's id). A finished workout never starts rest or touches the running timer, and "last time" is the session before it.
- **Pure core modules** (model, common, domain, database, data) use `liora.kmp.library` (Android and JVM targets). They must stay Android-free so the Ktor server can reuse them.
- **UI modules** use `liora.cmp.library` or `liora.cmp.feature` (Compose Multiplatform, Android target only for now). Keep UI code in `commonMain`, because a wasmJs target gets added for the web GUI.

## Data rules

- **Ids:** UUIDv7 strings from `IdGenerator`, created on the client. Built-in exercises use stable seed ids (`fedb.<id>`, `liora.<slug>`) and are never synced.
- **Units:** stored canonically (kg, m, s; body fat in percent). `Mass` wraps kilograms.
  - Display units are a setting (`Units`: kg/lb, km and m or miles and yards, cm/in). Composables read them from `LocalUnits` (`core:ui` `UnitLabels.kt`), which `MainActivity` provides. ViewModels that convert typed input take them from `SettingsRepository`.
  - Never hard-code kg, km or cm in UI. Show values through `weightText`, `volumeText` and `distanceText`, and body measurements through `BodyLabels.kt`. Convert typed values back with `Mass.of(value, unit)` and `LengthUnit.toMeters`.
- **Syncable rows:** embed `SyncMetadata` (`created_at`, `hlc`, `deleted_at`, `dirty`).
  - Every write goes through `SyncStamper` (`newRow`, `touch`, `tombstone`).
  - Never hard-delete synced rows.
  - Synced tables have no foreign-key constraints. Repositories keep references consistent and cascade tombstones.
  - `ownerId` is a server-side concept, added with the server (Phase 5). The local DB belongs to one user.
- **Local-only tables:** `local_meta` (device id, seed version, the running rest timer, the gym in use) and `exercise_name` (search index, rebuilt from the seed).
- **Photos:** progress photos are files in app-private `files/photos` (never the shared gallery), written only through `PhotoStorage`. Their rows sync; the images will sync as blobs (Phase 3).
- **Notes:** an exercise has its own note (`exercise_settings.sticky_note`, written through `ExerciseRepository.setNote`), shown wherever it appears; each workout has its own per exercise (`workout_exercise.notes`). Never copy one into the other. Routine exercise notes and custom exercise `notes` are legacy: `LegacyNotes` moves them onto the exercise's note at startup.
- **Settings:** read them from `SettingsRepository` (`settings` Flow, or `current()` inside a transaction), never hard-coded defaults: the e1RM formula, stall window and rest defaults all come from there. One `preference` row per choice; add a key in `SettingsCodec` for a new one.
- **Gyms and loads:** weights a user can load come from the gym in use (`GymProfileRepository.active`, per device). Round targets and steps through `LoadRounding` by the exercise's equipment, never a fixed 2.5 kg step. A gym keeps its own unit (pound plates stay 45 lb) while storing kilograms.
- **Derived data:** PRs and stats caches are recomputed locally and never synced.
- **Backups:** `BackupRepository` (`core/data/backup`) writes a ZIP of `liora.json` and the photos, and restores by
  merging row by row on HLC, like sync. The JSON format (`BackupFormat.kt`) is public and versioned: new fields
  are optional, and only a breaking change bumps `BackupJson.VERSION`. A new synced table also goes into the
  backup, which is how it syncs too: `BackupDao`, `BackupFormat.kt`, `BackupRows.kt`, the snapshot and merge in
  `SyncedTables`, and a `pushed…` query in `SyncDao`.
- **Sync:** `SyncEngine` (`core/data/sync`) pulls other devices' batches through a `SyncBackend`, then pushes
  this device's. A batch is a backup of the rows changed since the last push (`RowSelection.Changed`), deletions
  included, and merging one is restoring it, except that pulled rows aren't dirty. The workout in progress
  never syncs. The clock starts past the newest HLC merged (`NEWEST_MERGED_HLC`), so edits after a restart win.
  - Google Drive is `sync/gdrive`: REST in `commonMain` (Ktor, tested on the JVM with `MockEngine`), sign-in in
    `androidMain` (`GoogleDriveAccess`). The app runs syncs with WorkManager (`app/.../sync/`); UI tests get
    fakes from `TestLioraApplication.syncModules()` and never reach Google.
- **Health Connect:** `HealthRepository` (`core/data/health`) decides what goes out and comes in;
  `integration/healthconnect` is only the platform side. Readings never overwrite a day that has a value of its
  own. UI tests use `TestLioraApplication.healthModules()`.
- **Updates:** `UpdateRepository` (`core/data/update`) decides when to look and what's newer; `integration/github`
  reads the releases, and `app/.../update/` downloads (WorkManager), checks (`ApkCheck`: same key, higher
  version) and installs (`PackageInstaller`). Only the release build updates itself (`BuildConfig.UPDATES_FROM_GITHUB`).
  UI tests use `TestLioraApplication.updateModules()` and never reach GitHub.
- **Imports:** `core/domain/importing` reads Hevy and Strong CSVs and matches their exercise names
  (`ExerciseMatcher`); `ImportRepository` writes them. A name is never merged into a different exercise
  silently: anything not the same words becomes a custom variation. Tests use synthetic files in the real layouts.
- **Routine files:** routines an AI writes, in the public, versioned format of `docs/routine-format.md`
  (`liora-routine`, version 1; new fields optional, like the backup's). `RoutineFileReader` (`core/data/importing`)
  reads it and reports problems with where they are; `RoutineImportRepository` matches names with `ExerciseMatcher`
  and writes. Three things follow the reader and must change with it:
  - `tools/routine` ports the reader and the matcher to Node, so `check` says what the app will do.
  - The doc's JSON examples are imported by `RoutineImportFlowTest` (real catalog) and the tool's tests, so the
    doc, the tool and the app agree. Renaming a catalog exercise the examples use breaks them on purpose.
  - The app carries the doc (`feature/settings/.../composeResources/files/routine-format.md`) for "Copy the
    format for your AI": copy it over after editing the doc (a test checks they're equal).
- **Schema:** Room schemas are exported to `core/database/schemas/` and committed. Schema v1 is live on devices, so every change needs a version bump plus a migration (or `AutoMigration`) and a migration test.
- **Writes must outlive the screen:** don't pop a screen right after launching a write in its `viewModelScope`. Leaving the screen clears the ViewModel and cancels the write. Close in reaction to the new data instead (see `LoggerScreen`).

## Exercise catalog

- **Build:** `node tools/seed/build-seed.mjs` builds `app/android/src/main/assets/seed/exercises.json` from free-exercise-db (pinned commit, Unlicense).
- **Curation:**
  - `tools/seed/overrides.json` holds English renames, aliases per language, tracking-type fixes, popularity ranks and extra exercises.
  - `tools/seed/de-a.json` and `de-b.json` hold the German names.
  - German aliases also include Hevy's German wording, since people importing from Hevy search with those words.
- **Validation:** the script fails on missing translations, unknown names, duplicate ids and duplicate German names.
- **Versioning:** bump `SEED_VERSION` whenever the output changes. Devices re-seed built-ins by stable id, so history and settings survive.

## Conventions

- **Strings:** `src/commonMain/composeResources/values/strings.xml` per module, accessed with `stringResource(Res.string.x)`. Res lives in `<module namespace>.resources`. No hardcoded UI strings.
- **German:** every new string also goes into `values-de/strings.xml`, in the same change. English and German are both first-class languages.
  - Use plurals for counts.
  - Format numbers and dates through the locale-aware helpers, never string templates, so German shows "82,5 kg".
  - Exercise names are localized through `ExerciseName` rows, not string resources.
- **Voice:** empty states, the end of a workout, records and notifications are light and warm, with a wink, and still say what to do ("Nothing on the menu yet", "Call it a day?"). No emoji, and exclamation marks only for records. Write the German on its own, with its own idioms ("Der erste Satz fehlt noch"), not word for word. Anything that deletes, restores or explains stays plain.
- **Icons:** Material Symbols Rounded from google/material-design-icons (`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`).
  - When adding one, delete the `android:tint` attribute but keep the `>` that closes the tag.
  - Replace `@android:color/white` with `#FFFFFFFF`.
  - Expose it through `LioraIcons`.
- **Colors:** use `MaterialTheme.colorScheme` for standard roles and `LioraTheme.colors` for set types, PRs and completion. The accent is violet (#5504CF; #A57BFF for text and buttons in dark). Numbers that change live (timers, weights) get `.tabularNumbers()`.
- **Building blocks:** screens are a `LioraScaffold`, not Material's `Scaffold`; cards are `LioraCard`; rows of figures are `Stat`s in a `StatRow` (designsystem), never hand-made label/value columns; records are listed with `RecordsCard` (`core:ui`), with values from `RecordHighlights`; empty screens are an `EmptyState` with one of the `Illustration` scenes (designsystem, drawn in code in theme tints; a new one goes into `Scenes.kt` and shows up in `IllustrationsTest`). Avoid sentences that pack data together ("Bench: heaviest, best 1RM"): give each item a row or a chip. Rows of a list sit on one `LioraCard` with `LioraCardDefaults.listItemColors()`.
- **Liquid Glass** (`designsystem/glass`, on by default; Settings › Appearance turns it off): glass
  (`Modifier.glass`) blurs and bends a `GlassBackdrop` that `Modifier.glassSource` records from the content
  beneath; cards are panes (`glassPane`) over the aurora (`Modifier.aurora`, glows fixed to the window). Tokens
  are `LioraTheme.glass`; check `.enabled` and keep the solid look working.
  - `LioraScaffold` makes a screen's content the backdrop of its bars (`LocalGlassBackdrop`) and adds room for
    the tabs floating over it (`LocalFloatingChromeInsets`): lists pad their ends with the Scaffold's padding.
  - Glass never sits inside its own backdrop's source (it would draw itself), and a source draws an opaque
    background (the aurora) under its content, or the crisp content shows through the blur.
  - Android 13+ bends the rim (AGSL, `GlassLens.android.kt`), 12 only blurs, older phones draw it nearly solid.
- **Motion:** only drawing moves, so no tap waits for an animation; Compose already follows the system's "Remove animations". Figures that change live roll like an odometer (`Stat(motion = StatMotion.Roll)`, `RollingText`). Haptics go through `LioraHaptics` (designsystem), named by what happened (`setLogged`, `record`), never raw `HapticFeedbackType`s. Test motion with screenshots at fixed points, the test clock paused (see `LoggerFlowTest.tickingASetPopsTheCheckFillsTheRowAndRollsTheTotals`).
  - **Navigation motion:** screen transitions live in `app/android/.../ui/ScreenTransitions.kt` and follow `Navigator.lastMove`. A list item that opens a detail grows into it: put `rememberSharedContainer(SharedKeys.x(id))` on the item and on the detail's root (`core/navigation`). A screen that rises like a sheet takes `risingScreen()` as entry metadata. Celebrations use `ConfettiBurst` (designsystem).
- **Charts:** use the wrappers in `designsystem` (`LineChart`); only they touch Vico. Progress values and records format through `valueText`/`axisText` in `core:ui`, so units and locales stay consistent.
- **Versions:** all in `gradle/libs.versions.toml`. SDK levels and the JVM target are in `build-logic/.../LioraConfig.kt`.
- **Tests:**
  - **Pure core modules:** tests go in `src/jvmTest`. Use `inMemoryLioraDatabase()` for Room (bundled SQLite has JVM natives).
  - **CMP modules:** `src/commonTest` runs as Android host tests. Platform code is tested in `src/androidHostTest`.
  - **App-level UI tests:**
    - Use Robolectric with `@Config(sdk = [36], application = TestLioraApplication::class)`. That app uses an in-memory DB on the framework SQLite driver, because bundled SQLite only ships Android binaries.
    - Tests that launch `MainActivity` get Liquid Glass (the default setting); `LioraTheme(...)` without
      `glass = true` is the solid look. `LiquidGlassTest` covers glass on its own.
    - `stopKoin()` in `@After`.
    - The test app starts past the first-launch walkthrough; a test that wants it calls
      `WalkthroughRepository.showAgain()` (see `WalkthroughFlowTest`).
    - Room emits off the main thread, so use `waitUntil` (see `LioraAppTest.waitForText`) rather than asserting immediately.
    - Screens that depend on "now" (Progress, stalls) get a fixed clock: override Koin's `Clock` with `loadKoinModules` before anything uses it (see `ProgressFlowTest`), so screenshots don't change from day to day.
- **Translations:** `verifyTranslations` (part of `check`) fails when a `values/` string has no `values-de/` counterpart, or when a German string is stale. `translatable="false"` opts a string out.
- **Personal data:** `sample/` and `samples/` hold the user's real exports (e.g. Hevy) and are gitignored, so they exist only on the dev machine. The repo is public: tests use synthetic fixtures only, and no real workout data goes into commits.
- **Visual polish:** Phase 2 gives the app its character (playful design, motion); the look (Violet, in Liquid Glass since 2026-10-11) is settled, see PLAN.md. Typeface is Sora, bundled in `designsystem` with its OFL licence.
- **Foldables:** the Galaxy Z Fold 7 is the reference device. Its cover screen is about 411 dp wide (compact); the inner screen is about 984 × 1092 dp (expanded).
  - Layouts follow window size classes, never device checks. `currentWindowLayout()` (designsystem) holds the breakpoints: navigation rail from 600 dp, two panes from 840 dp.
  - Wide screens get a navigation rail and list-detail panes. Reading-heavy content is capped at about 600 dp with `Modifier.readableWidth()`.
  - State must survive fold and unfold. `MainActivity` handles size changes itself (`configChanges`), so folding re-lays out instead of recreating. `FoldableLayoutTest` simulates a fold with Robolectric.
  - The logger supports tabletop posture.
  - New screens get screenshot tests at cover and inner sizes (Robolectric qualifiers `w411dp-h960dp` and `w984dp-h1092dp`).

## Gotchas

- **AGP 9 KMP plugin:** the `kotlin { android { } }` DSL is reached from convention code through the `lioraAndroid {}` helper, since KGP's `androidTarget` is a different, legacy API.
- **Robolectric:** needs `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` on JDK 17+. It is already set in `configureHostTests()`.
  - Don't poll a repository with `runBlocking` inside `composeRule.waitUntil`. It blocks the main thread, so a ViewModel write that resumes on Main between its steps never finishes. Wait for what the screen shows, then read the repository once.
  - A text field inside a dialog (the logger's rename, history's save as routine) keeps Compose from ever going idle, even with the test clock paused. Don't open those dialogs in UI tests; cover the write in `core/data` tests and check the dialog on a device.
  - Material's date picker dialog fills in a moment after it opens: `waitUntil` its content appears. Each day's semantic text is the full date ("Monday, August 10, 2026"), not the day number.
  - Images: `TestLioraApplication`'s Coil loader decodes on the main thread with `BitmapFactory`, because Robolectric's native `ImageDecoder` fails on Windows. Tests that need photos draw them (`syntheticPhoto` in the app tests) and import them through the real `PhotoStorage`.
  - Material's `ExtendedFloatingActionButton` clears its text's semantics: give it a `contentDescription` (screen readers need it too) and find it by that in tests.
- **Lazy lists keep their first visible item in view by key.** Don't show trailing items (a footer card) while a screen is loading: when the content arrives above them, the list opens scrolled to the bottom.
- **Material3 version:** CMP `material3` is pinned to 1.9.0 (= androidx material3 1.4.0, the stable BOM version), not the alpha that ships alongside CMP 1.12.
- **Compose string resources** (`composeResources/**/strings.xml`) do not unescape `\'` the way Android `res/` does; the backslash shows up in the UI. Write a typographic apostrophe (`’`) instead.
- **Escaping in shell edits:** shell heredocs and sed here mangle backslashes and quotes. Make escaping-sensitive edits with the file edit tools, or with a script file, not inline shell text.
- **Invisible characters:** write them as `Char(0xFEFF)`, not `'﻿'`. The edit tools can turn the escape into the raw character, which Spotless then strips without a word.
- **Ktor requests:** inside `http.request { }`, a lambda parameter called `build` loses to the builder's own
  `build()`, and the request goes out unset. Name it something else and call it as `configure(this)`.
- **Spotless after editing `.editorconfig`** (e.g. the compose-rules allowlists): a lint result can come back
  from the build cache, or lag one run behind even with a fresh daemon. Rerun the module's
  `spotlessKotlin --rerun --no-build-cache`, and once more if the old allowlist still seems to apply.
- **Navigation 3 metadata** is stored under each `NavMetadataKey`'s `toString()`, so keys override it with a namespaced name (see `ListDetail.kt`).
- **Pinned versions:** `androidx.navigation3:navigation3-runtime` stays at 1.1.7 to match JB `navigation3-ui` 1.1.2, and `sqlite-bundled` stays at 2.6.2 to match Room 2.8.5. Lint's "newer version available" warnings on these two are expected.
