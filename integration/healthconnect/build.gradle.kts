plugins {
    alias(libs.plugins.liora.kmp.library)
}

// Health Connect on Android: finished workouts out as exercise sessions, weight and body fat in. What to
// write and what to keep is decided in core:data (HealthRepository); this is only the platform side.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.data)
        }
        androidMain.dependencies {
            implementation(libs.androidx.health.connect)
        }
    }
}
