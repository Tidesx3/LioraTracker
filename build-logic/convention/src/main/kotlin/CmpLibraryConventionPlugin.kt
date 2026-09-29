import app.liora.buildlogic.configureKotlinMultiplatform
import app.liora.buildlogic.configureHostTests
import app.liora.buildlogic.configureQuality
import app.liora.buildlogic.lib
import app.liora.buildlogic.libs
import app.liora.buildlogic.lioraAndroid
import app.liora.buildlogic.lioraNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Compose Multiplatform UI library. Android is the only target for now; a wasmJs target gets added
 * here when the web GUI arrives, so UI code must stay in commonMain wherever possible.
 */
class CmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        configureKotlinMultiplatform(includeJvm = false)
        configureQuality()
        configureHostTests()

        extensions.configure<KotlinMultiplatformExtension> {
            lioraAndroid {
                androidResources { enable = true }
                // commonTest runs as Android host (Robolectric-capable) tests, since there is no JVM target.
                withHostTestBuilder {}.configure {
                    isIncludeAndroidResources = true
                }
            }
            sourceSets.commonMain.dependencies {
                implementation(libs.lib("compose-runtime"))
                implementation(libs.lib("compose-foundation"))
                implementation(libs.lib("compose-ui"))
                implementation(libs.lib("compose-material3"))
                implementation(libs.lib("compose-components-resources"))
                implementation(libs.lib("compose-ui-tooling-preview"))
            }
        }

        // Generated `Res` lives next to the module's code, e.g. app.liora.feature.train.resources.Res
        extensions.configure<ComposeExtension> {
            (this as ExtensionAware).extensions.configure<ResourcesExtension>("resources") {
                packageOfResClass = "$lioraNamespace.resources"
            }
        }

        // Lets Android Studio render @Preview in library modules without shipping tooling in release.
        dependencies {
            "androidRuntimeClasspath"(platform(libs.lib("androidx-compose-bom")))
            "androidRuntimeClasspath"(libs.lib("androidx-compose-ui-tooling"))
        }
    }
}
