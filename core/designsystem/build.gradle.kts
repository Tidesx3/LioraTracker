plugins {
    alias(libs.plugins.liora.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material3.adaptive)
            // Dates in the date formatter's API.
            api(libs.kotlinx.datetime)
        }
        // The date formatter asks Android's ICU for each locale's patterns.
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
        }
    }
}
