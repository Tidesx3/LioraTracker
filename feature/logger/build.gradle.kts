plugins {
    alias(libs.plugins.liora.cmp.feature)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.reorderable)
    }
}
