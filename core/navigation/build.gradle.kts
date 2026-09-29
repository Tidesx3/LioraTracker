plugins {
    alias(libs.plugins.liora.cmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.androidx.navigation3.runtime)
        implementation(libs.jb.navigation3.ui)
        implementation(libs.kotlinx.serialization.json)
    }
}
