# Liora

A workout tracker that is fast to log with, owned by its user, and built to grow into an ecosystem:
an offline-first Android app now, then sync to your own Google Drive, a self-hosted server with a web
GUI, and AI features on top.

**Status:** Phases 1 (the offline MVP) and 2 (design and motion) are done: the app has its look, a vivid
violet with the Sora typeface, floating cards and pills, small drawn scenes on empty screens, and a
voice of its own in English and German. Logging moves: a ticked set's check pops and its row fills, the totals and the rest
countdown roll like an odometer, haptics mark sets and records, and a record bursts into confetti.
Cards grow into the screens they open, back follows the finger, and the logger rises from the workout
bar. The database, domain logic, exercise library with
custom exercises, German, the foldable layouts, routines with folders and supersets, and the logger
(number pad, rest timer, live workout notification, personal records, tabletop mode) are done. History
is done too: list, calendar, workout detail (repeat, save as routine, delete) and editing past workouts
in the logger, times included. Each exercise's page charts its progress (e1RM, weight, volume, reps),
lists its records and sessions, and flags a stall. The Progress tab shows the weekly streak, a
consistency grid, sets per muscle on a body heatmap, every exercise's best, and a monthly report.
Body tracks bodyweight, body fat and circumferences day by day, with a chart and the change over the
month, and keeps progress photos private on the phone, compared side by side with the weight change
between them. Settings cover rest defaults, an RPE column in the logger, units (kg or lb, km or miles, cm
or inches, shown and typed everywhere), the e1RM formula, the stall window, theme and language. Gyms
describe their bars, plates, dumbbells and machine stacks: the logger's ± steps go to weights the gym
can load, show the plates per side for a barbell, and add warm-up sets. Everything backs up into one
file (readable JSON plus the photos) that restores on another phone without overwriting newer changes,
and workouts export as CSV for spreadsheets. Workouts and body measurements import from Hevy and Strong
exports, with a review of which exercise each name becomes; gym-specific machines become variations of
their own. Phase 3 is done: everything syncs through a hidden folder in your own Google Drive (Settings
› Your data), in the background, deletions and photos included, with what's still waiting and the space it
takes in view. Health Connect gets every finished workout and brings in weight and body fat from apps like
Samsung Health. On the first start, a short walkthrough shows how Liora works, asks kilograms or pounds,
and offers sync. Phase 4 has begun: a routine's exercises can use double progression, so the next workout
starts a step heavier once the sets reach the top of their rep ranges, and says why (Settings › Progression
tunes how). A folder of routines can be a rotation, and the Train tab says which one is next, as does a
home-screen widget, which follows the workout while it runs. On Android 16 the workout is a Live Update: the
set and the rest in the status bar and on the lock screen. Updates from GitHub follow. Details and progress
are in [docs/PLAN.md](docs/PLAN.md).

## Principles

- **Logging speed first.** One tap per set, previous values inline, and a rest timer that starts itself.
- **Your data.** No paywalls or limits, full export at any time, local-first, and sync to storage you control.
- **Never lose a workout.** The active session is persisted on every change.
- **Clean foundations.** Kotlin Multiplatform modules that the Android app, the server and the web client share.
- **Made for foldables too.** Adaptive layouts for phones and foldables such as the Galaxy Z Fold 7: two panes on the inner screen, and a tabletop logger.

## Stack

Kotlin Multiplatform · Compose Multiplatform (Android target today, wasm later) · Navigation 3 · Koin ·
Room KMP · kotlinx.coroutines/serialization · Gradle convention plugins · Spotless/ktlint · detekt ·
Robolectric + Roborazzi.

## Getting started

Requirements: JDK 21 and the Android SDK (platform 37). Android Studio provides both.

```sh
./gradlew :app:android:installDebug           # build and install on a connected device/emulator
./gradlew check                               # tests, formatting, detekt, lint
./gradlew spotlessApply                       # format
./gradlew :app:android:recordRoborazziDebug   # refresh UI screenshots in app/android/src/test/screenshots
```

### Release builds

Debug builds are much slower than what you'd ship: the code isn't optimized or precompiled. Judge speed on a
release build. It installs as its own app (`app.liora`, debug is `app.liora.debug`) with its own data; move
workouts over with Settings › Back up and Restore.

Release builds are signed with the key named in `keystore.properties` at the repo root (gitignored). Without
that file they come out unsigned and won't install. To set one up:

```sh
keytool -genkeypair -keystore ~/.liora/liora-release.jks -alias liora -keyalg RSA -keysize 4096 -validity 10000
```

```properties
storeFile=C:/Users/you/.liora/liora-release.jks
storePassword=...
keyAlias=liora
keyPassword=...
```

Keep a copy of both files somewhere safe: a release build can only be updated with the key it was signed with.

```sh
./gradlew :app:android:installRelease
```

### Publishing a release

Releases are built and signed on this machine and published on GitHub, where the release app looks for
updates once a day (Settings › About Liora). Set the version in `version.properties`, write what's new in
`docs/releases/<version>.md` (an `## English` and a `## Deutsch` section; the app shows the one in its
language), commit and push, then:

```sh
node tools/release/release.mjs --dry-run   # builds and checks it, publishes nothing
node tools/release/release.mjs             # tags v<version> and publishes liora-<version>.apk
```

It runs `check`, makes sure the APK is `app.liora` at that version and signed with the release key, then
tags and publishes with `gh`. Only the release build updates itself; debug builds never look.

## Layout

```
app/android        Android application: shell, navigation host, DI wiring
core/model         Pure domain types (KMP, also JVM for the future server)
core/common        Ids (UUIDv7), shared utilities
core/domain        Calculators and business rules (PRs, e1RM, volume, imports)
core/database      Room KMP database
core/data          Repositories (offline-first)
core/navigation    Route keys + tab-aware navigator
core/designsystem  Theme, icons, shared components
core/ui            Composables that know the domain: units, labels, records, the update card
feature/*          One module per app area: train, logger, history, exercises, progress, body, settings
sync/gdrive        Sync through the user's Google Drive
integration/*      Health Connect, and Liora's releases on GitHub
tools/             Exercise catalog, icons, rest sounds, and publishing releases
build-logic        Gradle convention plugins (liora.kmp.library, liora.cmp.feature, ...)
```

## Roadmap

0. Scaffold, design system, app shell ✅
1. Offline MVP: exercise library with unlimited custom exercises, routines, the active logger with
   rest timer and a live workout notification (next set, rest countdown, lock-screen actions),
   history, progress charts and PRs, body metrics and photos, JSON/CSV export, Hevy/Strong import,
   and English + German
2. Design and motion polish: more character, a bit quirky and playful, with animations that make
   logging feel good
3. Google Drive (appDataFolder) sync and Health Connect
4. Double progression, rotations, a home-screen widget, the workout on the lock screen, and updates from
   GitHub
5. Self-hosted Ktor + PostgreSQL server (Docker) and web GUI
6. Opt-in AI on that server: ask your log, insights, natural-language logging, program generation
