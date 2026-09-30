plugins {
    alias(libs.plugins.liora.cmp.library)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.model)
        // Labels for domain concepts such as record types.
        implementation(projects.core.domain)
        implementation(projects.core.designsystem)
        implementation(libs.coil.compose)
    }
}
