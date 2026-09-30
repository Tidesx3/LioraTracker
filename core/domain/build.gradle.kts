plugins {
    alias(libs.plugins.liora.kmp.library)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.model)
        implementation(projects.core.common)
        // Calendar days and months for history and, later, streaks.
        api(libs.kotlinx.datetime)
    }
}
