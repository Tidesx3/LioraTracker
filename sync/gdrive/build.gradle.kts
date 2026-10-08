plugins {
    alias(libs.plugins.liora.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Sync to the user's Google Drive: the app data folder through Drive's REST API (common, tested on the
// JVM), and signing in through Google Play services (Android).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.services.auth)
            implementation(libs.kotlinx.coroutines.play.services)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
