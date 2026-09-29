# Liora: notes for Claude

A workout tracker (Android first, KMP) for personal use and self-hosting. See README.md for the product
summary and roadmap.

## Commands

The JDK isn't on PATH in every shell. Export it first when needed:
`export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"`

- `./gradlew check`: all tests plus Spotless, detekt and Android Lint. Run it before committing.
- `./gradlew spotlessApply`: formats code (ktlint_official style plus compose-rules).
- `./gradlew :app:android:assembleDebug` or `installDebug`.
- `./gradlew :app:android:recordRoborazziDebug`: re-renders screenshots to `app/android/src/test/screenshots/`. Read the PNGs to review UI changes.

## Architecture rules

- **Offline-first.** The UI reads Flows from repositories, and repositories write through to the local DB (Room, from Phase 1).
- **Dependencies:** `feature/*` depends only on `core/*`. Features never depend on each other. Cross-feature navigation goes through route keys in `core/navigation/Routes.kt` and the `Navigator`. `app/android` wires everything together (Koin modules, `entryProvider`).
- **Feature pattern:**
  - Each screen is a stateful `XScreen(viewModel = koinViewModel())` wrapping a stateless private `XContent(uiState, callbacks)`.
  - `XNavigation.kt` exposes `fun EntryProviderScope<NavKey>.xEntries(navigator)` and a Koin `xModule`.
  - To add a feature: register its entries in `LioraApp.kt` and its module in `LioraApplication.kt`.
- **Pure core modules** (model, common, domain, database, data) use `liora.kmp.library` (Android and JVM targets). They must stay Android-free so the Ktor server can reuse them.
- **UI modules** use `liora.cmp.library` or `liora.cmp.feature` (Compose Multiplatform, Android target only for now). Keep UI code in `commonMain`, because a wasmJs target gets added for the web GUI.

## Data rules

- **Ids:** UUIDv7 strings from `IdGenerator`, created on the client. Built-in exercises use stable seed ids (`fedb.<id>`, `liora.<slug>`) and are never synced.
- **Units:** stored canonically (kg, m, s). Display units are only a preference. `Mass` wraps kilograms.
- **Syncable rows:** embed `SyncMetadata` (`created_at`, `hlc`, `deleted_at`, `dirty`).
  - Every write goes through `SyncStamper` (`newRow`, `touch`, `tombstone`).
  - Never hard-delete synced rows.
  - Synced tables have no foreign-key constraints. Repositories keep references consistent and cascade tombstones.
  - `ownerId` is a server-side concept, added in Phase 3. The local DB belongs to one user.
- **Local-only tables:** `local_meta` (device id, seed version) and `exercise_name` (search index, rebuilt from the seed).
- **Derived data:** PRs and stats caches are recomputed locally and never synced.
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
- **Icons:** Material Symbols Rounded from google/material-design-icons (`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`).
  - When adding one, delete the `android:tint` attribute but keep the `>` that closes the tag.
  - Replace `@android:color/white` with `#FFFFFFFF`.
  - Expose it through `LioraIcons`.
- **Colors:** use `MaterialTheme.colorScheme` for standard roles and `LioraTheme.colors` for set types, PRs and completion. Numbers that change live (timers, weights) get `.tabularNumbers()`.
- **Versions:** all in `gradle/libs.versions.toml`. SDK levels and the JVM target are in `build-logic/.../LioraConfig.kt`.
- **Tests:**
  - **Pure core modules:** tests go in `src/jvmTest`. Use `inMemoryLioraDatabase()` for Room (bundled SQLite has JVM natives).
  - **CMP modules:** `src/commonTest` runs as Android host tests. Platform code is tested in `src/androidHostTest`.
  - **App-level UI tests:**
    - Use Robolectric with `@Config(sdk = [36], application = TestLioraApplication::class)`. That app uses an in-memory DB on the framework SQLite driver, because bundled SQLite only ships Android binaries.
    - `stopKoin()` in `@After`.
    - Room emits off the main thread, so use `waitUntil` (see `LioraAppTest.waitForText`) rather than asserting immediately.
- **Translations:** `verifyTranslations` (part of `check`) fails when a `values/` string has no `values-de/` counterpart, or when a German string is stale. `translatable="false"` opts a string out.
- **Personal data:** `sample/` holds the user's real exports (e.g. Hevy) and is gitignored. Tests use synthetic fixtures only.

## Gotchas

- **AGP 9 KMP plugin:** the `kotlin { android { } }` DSL is reached from convention code through the `lioraAndroid {}` helper, since KGP's `androidTarget` is a different, legacy API.
- **Robolectric:** needs `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` on JDK 17+. It is already set in `configureHostTests()`.
- **Material3 version:** CMP `material3` is pinned to 1.9.0 (= androidx material3 1.4.0, the stable BOM version), not the alpha that ships alongside CMP 1.12.
- **Pinned versions:** `androidx.navigation3:navigation3-runtime` stays at 1.1.7 to match JB `navigation3-ui` 1.1.2, and `sqlite-bundled` stays at 2.6.2 to match Room 2.8.5. Lint's "newer version available" warnings on these two are expected.
