import app.liora.buildlogic.LioraConfig
import app.liora.buildlogic.configureHostTests
import app.liora.buildlogic.configureQuality
import app.liora.buildlogic.configureTranslationCheck
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android application module. AGP 9 compiles Kotlin itself (built-in Kotlin), so no kotlin-android plugin. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        configureQuality()
        configureHostTests()
        configureTranslationCheck("src/main/res")

        extensions.configure<ApplicationExtension> {
            compileSdk = LioraConfig.COMPILE_SDK
            defaultConfig {
                minSdk = LioraConfig.MIN_SDK
                targetSdk = LioraConfig.TARGET_SDK
            }
            compileOptions {
                sourceCompatibility = LioraConfig.javaVersion
                targetCompatibility = LioraConfig.javaVersion
            }
            buildFeatures {
                compose = true
                buildConfig = true
            }
        }
    }
}
