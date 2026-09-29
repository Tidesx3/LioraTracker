package app.liora.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Formatting (Spotless + ktlint + compose-rules) and static analysis (detekt) for every module. */
internal fun Project.configureQuality() {
    pluginManager.apply("com.diffplug.spotless")
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    val ktlintVersion = libs.version("ktlint")
    val composeRules = libs.lib("composeRules-ktlint").get().toString()

    // Targets are rooted in src/ and the build script: a project-wide glob would walk build/, which
    // parallel tasks are writing to (flaky on Windows).
    val sources = fileTree("src") { include("**/*.kt") }
    val buildScript = file("build.gradle.kts")
    extensions.configure<SpotlessExtension> {
        kotlin {
            target(sources)
            ktlint(ktlintVersion).customRuleSets(listOf(composeRules))
        }
        kotlinGradle {
            target(buildScript)
            ktlint(ktlintVersion)
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom(files("src"))
    }
}
