import androidx.room.gradle.RoomExtension
import app.liora.buildlogic.lib
import app.liora.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Room KMP with the bundled SQLite driver. Schemas are exported to `schemas/` and committed so
 * migrations can be tested against every released version. Apply after liora.kmp.library.
 */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                api(libs.lib("room-runtime"))
                implementation(libs.lib("sqlite-bundled"))
            }
        }

        dependencies {
            "kspAndroid"(libs.lib("room-compiler"))
            "kspJvm"(libs.lib("room-compiler"))
        }
    }
}
