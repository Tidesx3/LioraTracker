plugins {
    alias(libs.plugins.liora.cmp.library)
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.compose.material3.adaptive)
    }
}
