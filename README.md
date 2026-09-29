# Liora

A workout tracker that is fast to log with, owned by its user, and built to grow into an ecosystem:
an offline-first Android app now, then sync to your own Google Drive, a self-hosted server with a web
GUI, and AI features on top.

**Status:** Phase 1 (the offline MVP) is in progress. The database, domain logic, exercise library with
custom exercises, German, the foldable layouts, and routines with folders and supersets are done. The
logger is next.

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
feature/*          One module per app area: train, logger, history, exercises, progress, body, settings
build-logic        Gradle convention plugins (liora.kmp.library, liora.cmp.feature, ...)
```

## Roadmap

0. Scaffold, design system, app shell ✅
1. Offline MVP: exercise library with unlimited custom exercises, routines, the active logger with
   rest timer and a live workout notification (next set, rest countdown, lock-screen actions),
   history, progress charts and PRs, body metrics and photos, JSON/CSV export, Hevy/Strong import,
   and English + German
2. Google Drive (appDataFolder) sync and Health Connect
3. Self-hosted Ktor + PostgreSQL server (Docker)
4. Web GUI, programs with auto-progression, Wear OS
5. Opt-in AI: ask your log, insights, natural-language logging, program generation
