import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "app.liora.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = libs.plugins.liora.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("cmpLibrary") {
            id = libs.plugins.liora.cmp.library.get().pluginId
            implementationClass = "CmpLibraryConventionPlugin"
        }
        register("cmpFeature") {
            id = libs.plugins.liora.cmp.feature.get().pluginId
            implementationClass = "CmpFeatureConventionPlugin"
        }
        register("androidApplication") {
            id = libs.plugins.liora.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("room") {
            id = libs.plugins.liora.room.get().pluginId
            implementationClass = "RoomConventionPlugin"
        }
    }
}
