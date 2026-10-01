plugins {
    alias(libs.plugins.liora.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material3.adaptive)
            // Dates in the date formatter's API.
            api(libs.kotlinx.datetime)
            // Behind the chart wrappers; features use those, not Vico itself.
            implementation(libs.vico.multiplatform)
            implementation(libs.vico.multiplatform.m3)
        }
        // The date formatter asks Android's ICU for each locale's patterns.
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
        }
    }
}
