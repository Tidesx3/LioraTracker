import app.liora.buildlogic.lib
import app.liora.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature = screens + ViewModels for one area of the app. Features depend on core modules only,
 * never on each other; cross-feature navigation goes through route keys in :core:navigation.
 */
class CmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("liora.cmp.library")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                implementation(project(":core:model"))
                implementation(project(":core:data"))
                implementation(project(":core:navigation"))
                implementation(project(":core:designsystem"))
                implementation(project(":core:ui"))
                implementation(project(":core:domain"))

                implementation(libs.lib("jb-lifecycle-runtime-compose"))
                implementation(libs.lib("jb-lifecycle-viewmodel-compose"))
                implementation(libs.lib("jb-navigation3-ui"))
                implementation(libs.lib("koin-compose"))
                implementation(libs.lib("koin-compose-viewmodel"))
                implementation(libs.lib("koin-core-viewmodel"))
            }
        }
    }
}
