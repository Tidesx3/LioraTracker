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

## Data rules (from Phase 1)

- **Ids:** UUIDv7 strings from `IdGenerator`, created on the client.
- **Units:** stored canonically (kg, m, s). Display units are only a preference. `Mass` wraps kilograms.
- **Syncable rows:** carry `ownerId`, `createdAt`, `updatedAt`, `deletedAt` (tombstone) and a local `dirty` flag.
- **Derived data:** PRs and stats caches are recomputed locally and never synced.

## Conventions

- **Strings:** `src/commonMain/composeResources/values/strings.xml` per module, accessed with `stringResource(Res.string.x)`. Res lives in `<module namespace>.resources`. No hardcoded UI strings.
- **Icons:** Material Symbols Rounded from google/material-design-icons (`symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`).
  - When adding one, delete the `android:tint` attribute but keep the `>` that closes the tag.
  - Replace `@android:color/white` with `#FFFFFFFF`.
  - Expose it through `LioraIcons`.
- **Colors:** use `MaterialTheme.colorScheme` for standard roles and `LioraTheme.colors` for set types, PRs and completion. Numbers that change live (timers, weights) get `.tabularNumbers()`.
- **Versions:** all in `gradle/libs.versions.toml`. SDK levels and the JVM target are in `build-logic/.../LioraConfig.kt`.
- **Tests:**
  - Pure core modules put tests in `src/jvmTest`, which is fast and lets Room's bundled SQLite work.
  - CMP modules use `src/commonTest`, which runs as Android host tests.
  - App-level UI tests use Robolectric (`@Config(sdk = [36])`) and must `stopKoin()` in `@After`.

## Gotchas

- **AGP 9 KMP plugin:** the `kotlin { android { } }` DSL is reached from convention code through the `lioraAndroid {}` helper, since KGP's `androidTarget` is a different, legacy API.
- **Robolectric:** needs `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` on JDK 17+. It is already set in `configureHostTests()`.
- **Material3 version:** CMP `material3` is pinned to 1.9.0 (= androidx material3 1.4.0, the stable BOM version), not the alpha that ships alongside CMP 1.12.
- **Pinned versions:** `androidx.navigation3:navigation3-runtime` stays at 1.1.7 to match JB `navigation3-ui` 1.1.2, and `sqlite-bundled` stays at 2.6.2 to match Room 2.8.5. Lint's "newer version available" warnings on these two are expected.
