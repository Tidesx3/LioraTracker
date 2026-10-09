plugins {
    alias(libs.plugins.liora.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Liora's releases on GitHub (PLAN 4e): the latest release through GitHub's REST API, and its APK
// downloaded. Common code, tested on the JVM; what to do with them is decided in core:data
// (UpdateRepository) and the app installs them.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
            implementation(projects.core.domain)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
