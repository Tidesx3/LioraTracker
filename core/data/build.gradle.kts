plugins {
    alias(libs.plugins.liora.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
