// Speed on a real phone: startup, scrolling and logging, measured on a release build (macrobenchmarks), and
// the journeys the app's baseline profile is collected from. README › Measuring speed says how to run them.
plugins {
    alias(libs.plugins.liora.android.benchmark)
    alias(libs.plugins.baselineprofile)
}

android {
    // Also the test APK's package, which can't be the measured app's (app.liora.benchmark).
    namespace = "app.liora.benchmark.test"
    targetProjectPath = ":app:android"
}

baselineProfile {
    // The phone over adb, never an emulator: an emulator's timings say nothing about the Fold's.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
