plugins {
    alias(libs.plugins.liora.kmp.library)
    alias(libs.plugins.liora.room)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(projects.core.model)
        implementation(projects.core.common)
    }
}
