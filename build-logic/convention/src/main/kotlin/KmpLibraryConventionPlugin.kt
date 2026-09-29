import app.liora.buildlogic.configureKotlinMultiplatform
import app.liora.buildlogic.configureQuality
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Pure Kotlin library shared across Android, the JVM server and (later) web. No Compose. */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        configureKotlinMultiplatform(includeJvm = true)
        configureQuality()
    }
}
