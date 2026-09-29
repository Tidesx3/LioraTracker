package app.liora.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Registers `verifyTranslations` for a resource root (Compose `composeResources` or Android `res`) and
 * hooks it into `check`, so a string without a German translation (or a stale German string) fails the
 * build instead of shipping a half-translated screen.
 */
internal fun Project.configureTranslationCheck(resourceRoot: String) {
    val root = layout.projectDirectory.dir(resourceRoot)
    if (!root.asFile.exists()) return
    val verify =
        tasks.register<VerifyTranslationsTask>("verifyTranslations") {
            group = "verification"
            description = "Checks that every string resource is translated into ${LioraConfig.TRANSLATED_LOCALES}."
            resources.set(root)
            displayPath.set("$path ($resourceRoot)")
            locales.set(LioraConfig.TRANSLATED_LOCALES)
            report.set(layout.buildDirectory.file("reports/translations.txt"))
        }
    tasks.named("check") { dependsOn(verify) }
}

@CacheableTask
abstract class VerifyTranslationsTask : DefaultTask() {
    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resources: DirectoryProperty

    @get:Input
    abstract val locales: ListProperty<String>

    @get:Input
    abstract val displayPath: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val root = resources.get().asFile
        val defaultKeys = keysIn(File(root, "values"), onlyTranslatable = true)
        val problems =
            locales.get().flatMap { locale ->
                val localized = keysIn(File(root, "values-$locale"), onlyTranslatable = false)
                (defaultKeys - localized).map { "missing in values-$locale: $it" } +
                    (localized - keysIn(File(root, "values"), onlyTranslatable = false))
                        .map { "stale in values-$locale (not in values): $it" }
            }
        report.get().asFile.writeText(problems.joinToString("\n", postfix = "\n"))
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Translations incomplete in ${displayPath.get()}:\n  " + problems.joinToString("\n  "),
            )
        }
    }

    private fun keysIn(
        directory: File,
        onlyTranslatable: Boolean,
    ): Set<String> {
        val files = directory.listFiles { file -> file.extension == "xml" }.orEmpty()
        return files
            .flatMap { file ->
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val entries = document.documentElement.childNodes
                (0 until entries.length).mapNotNull { index ->
                    val element = entries.item(index) as? Element ?: return@mapNotNull null
                    val isString = element.tagName in STRING_TAGS
                    val translatable = element.getAttribute("translatable") != "false"
                    element.getAttribute("name").takeIf { isString && (translatable || !onlyTranslatable) }
                }
            }.toSet()
    }

    private companion object {
        val STRING_TAGS = setOf("string", "plurals", "string-array")
    }
}
