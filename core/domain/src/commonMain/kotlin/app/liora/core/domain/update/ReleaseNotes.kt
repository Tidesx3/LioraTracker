package app.liora.core.domain.update

/**
 * What's new in a release, as Liora shows it. Release notes are written in English and German, as
 * `## English` and `## Deutsch` sections (docs/releases/); Liora shows the one in its language, English
 * otherwise, or the whole text when it has no sections. List items become bullets.
 */
object ReleaseNotes {
    fun forLanguage(
        notes: String,
        language: String,
    ): String {
        val sections = sections(notes)
        val text = sections[language] ?: sections[ENGLISH] ?: notes
        return text.lines().joinToString("\n", transform = ::bullet).trim()
    }

    /** "- Rotations" as "• Rotations"; any other line as it is. */
    private fun bullet(line: String): String {
        val marker = LIST_ITEM.find(line) ?: return line
        return "• " + line.substring(marker.range.last + 1)
    }

    /** The text under each language's heading, by language code. */
    private fun sections(notes: String): Map<String, String> {
        val sections = mutableMapOf<String, StringBuilder>()
        var current: StringBuilder? = null
        for (line in notes.lines()) {
            val heading = HEADING.matchEntire(line.trim())
            if (heading != null) {
                current =
                    LANGUAGES[heading.groupValues[1].trim().lowercase()]?.let { sections.getOrPut(it, ::StringBuilder) }
            } else {
                current?.appendLine(line)
            }
        }
        return sections.mapValues { it.value.toString() }
    }

    private const val ENGLISH = "en"
    private val LANGUAGES = mapOf("english" to ENGLISH, "deutsch" to "de", "german" to "de")
    private val HEADING = Regex("""##\s+(.+)""")
    private val LIST_ITEM = Regex("""^\s*[-*]\s+""")
}
