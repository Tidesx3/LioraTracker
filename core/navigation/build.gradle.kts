plugins {
    alias(libs.plugins.liora.cmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.androidx.navigation3.runtime)
        implementation(libs.kotlinx.serialization.json)
    }
}
