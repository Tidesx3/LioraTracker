package app.liora.core.domain.importing

/**
 * CSV as RFC 4180 has it, and as apps in other languages write it: fields are split by commas, or by
 * semicolons when the header holds more of those (where the comma is the decimal mark). Quoted fields
 * may hold separators, doubled quotes and line breaks. A leading byte order mark is dropped, lines may
 * end in CRLF, LF or CR, and blank lines are skipped.
 */
internal object Csv {
    fun read(text: String): List<List<String>> {
        // Line breaks in quoted notes become plain ones too.
        val body =
            text
                .removePrefix(BYTE_ORDER_MARK.toString())
                .replace("\r\n", "\n")
                .replace('\r', '\n')
        val parser = Parser(separatorOf(body))
        var index = 0
        while (index < body.length) {
            index += if (parser.take(body[index], body.getOrNull(index + 1))) 2 else 1
        }
        return parser.finish()
    }

    /** Splits text into rows and fields, one character at a time. */
    private class Parser(
        private val separator: Char,
    ) {
        private val rows = mutableListOf<List<String>>()
        private var row = mutableListOf<String>()
        private val field = StringBuilder()
        private var quoted = false

        /** Takes [char], with [next] after it; returns whether it took [next] as well. */
        fun take(
            char: Char,
            next: Char?,
        ): Boolean {
            if (quoted) return takeQuoted(char, next)
            when (char) {
                '"' -> if (field.isEmpty()) quoted = true else field.append(char)
                separator -> endField()
                '\n' -> endRow()
                else -> field.append(char)
            }
            return false
        }

        fun finish(): List<List<String>> {
            if (field.isNotEmpty() || row.isNotEmpty()) endRow()
            return rows
        }

        /** Inside quotes, a doubled quote is one quote, and a single one ends them. */
        private fun takeQuoted(
            char: Char,
            next: Char?,
        ): Boolean {
            val doubled = char == '"' && next == '"'
            when {
                doubled -> field.append('"')
                char == '"' -> quoted = false
                else -> field.append(char)
            }
            return doubled
        }

        private fun endField() {
            row += field.toString()
            field.clear()
        }

        private fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows += row
            row = mutableListOf()
        }
    }

    /** A semicolon when the first line has more of them than commas, outside quotes; else a comma. */
    private fun separatorOf(text: String): Char {
        var commas = 0
        var semicolons = 0
        var quoted = false
        for (char in text) {
            when {
                char == '"' -> quoted = !quoted
                quoted -> Unit
                char == '\n' -> break
                char == ',' -> commas++
                char == ';' -> semicolons++
            }
        }
        return if (semicolons > commas) ';' else ','
    }

    private val BYTE_ORDER_MARK = Char(0xFEFF)
}

/** A CSV file read by its header: each row's fields by column name, whatever order the columns come in. */
internal class CsvTable(
    rows: List<List<String>>,
) {
    private val columns: Map<String, Int> =
        rows
            .firstOrNull()
            .orEmpty()
            .withIndex()
            .associate { (index, name) -> normalizeColumn(name) to index }

    val rows: List<Row> = rows.drop(1).map(::Row)

    /** Whether the file has [column], named in any case. */
    fun has(column: String): Boolean = normalizeColumn(column) in columns

    /** The first of [names] the file has. */
    fun firstOf(vararg names: String): String? = names.firstOrNull(::has)

    inner class Row(
        private val fields: List<String>,
    ) {
        /** The field in [column], trimmed; null when it's empty or the file has no such column. */
        operator fun get(column: String?): String? =
            column
                ?.let { columns[normalizeColumn(it)] }
                ?.let(fields::getOrNull)
                ?.trim()
                ?.ifEmpty { null }
    }

    companion object {
        fun of(text: String) = CsvTable(Csv.read(text))

        private fun normalizeColumn(name: String) = name.trim().lowercase()
    }
}

/** A number as exports write it, with a decimal point or a decimal comma. */
internal fun parseNumber(text: String?): Double? =
    text
        ?.trim()
        ?.replace(',', '.')
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() }
