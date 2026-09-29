plugins {
    alias(libs.plugins.liora.cmp.library)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.core.model)
        implementation(projects.core.designsystem)
    }
}
