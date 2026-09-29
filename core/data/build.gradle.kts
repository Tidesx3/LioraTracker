plugins {
    alias(libs.plugins.liora.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            // Logging rules (next set, rest, placeholders) are shared by the logger and the notification.
            api(projects.core.domain)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
