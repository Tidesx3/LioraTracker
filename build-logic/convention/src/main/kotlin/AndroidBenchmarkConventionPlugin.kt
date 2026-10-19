import app.liora.buildlogic.LioraConfig
import app.liora.buildlogic.configureQuality
import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The macrobenchmark module: tests that run on a phone against the app's release build, and the journeys
 * its baseline profile is made from. Built-in Kotlin, like the application.
 */
class AndroidBenchmarkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        configureQuality()

        extensions.configure<TestExtension> {
            compileSdk = LioraConfig.COMPILE_SDK
            defaultConfig {
                minSdk = LioraConfig.BENCHMARK_MIN_SDK
                targetSdk = LioraConfig.TARGET_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = LioraConfig.javaVersion
                targetCompatibility = LioraConfig.javaVersion
            }
            // The tests drive the app from their own process, so stopping the app doesn't stop them.
            experimentalProperties["android.experimental.self-instrumenting"] = true
        }
    }
}
