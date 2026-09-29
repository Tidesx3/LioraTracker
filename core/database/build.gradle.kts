plugins {
    alias(libs.plugins.liora.kmp.library)
    alias(libs.plugins.liora.room)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            implementation(projects.core.common)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        jvmTest.dependencies {
            implementation(libs.room.testing)
        }
    }
}
