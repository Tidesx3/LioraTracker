package app.liora.core.domain

import app.liora.core.model.Exercise

/**
 * Fast, forgiving exercise search over names and aliases in every language.
 *
 * - Case, umlauts and their spelled-out forms are ignored: "Bankdrücken", "bankdrucken" and
 *   "Bankdruecken" all match.
 * - Word order and punctuation don't matter: "press bench" and "pull-up" / "pullup" both work.
 * - Typos are tolerated: "Kreuzhebn" still finds deadlifts.
 *
 * Ties are broken by popularity rank, so common lifts come before obscure variations.
 */
class ExerciseSearch(
    exercises: List<Exercise>,
) {
    private val entries =
        exercises.map { exercise ->
            Entry(
                exercise = exercise,
                display = SearchText(exercise.name),
                others = exercise.searchTerms.map(::SearchText),
            )
        }

    fun search(
        query: String,
        includeArchived: Boolean = false,
    ): List<Exercise> {
        val candidates = entries.filter { includeArchived || !it.exercise.settings.archived }
        val normalizedQuery = SearchText(query)
        if (normalizedQuery.tokens.isEmpty()) {
            return candidates.map { it.exercise }.sortedWith(byRankThenName)
        }
        return candidates
            .mapNotNull { entry ->
                val displayScore = score(normalizedQuery, entry.display)
                val bestOther = entry.others.maxOfOrNull { score(normalizedQuery, it) } ?: 0
                val boostedDisplay = if (displayScore > 0) displayScore + DISPLAY_NAME_BONUS else 0
                val best = maxOf(boostedDisplay, bestOther)
                if (best > 0) entry.exercise to best else null
            }.sortedWith(
                compareByDescending<Pair<Exercise, Int>> { it.second }
                    .thenBy { it.first.rank ?: Int.MAX_VALUE }
                    .thenBy { it.first.name.length }
                    .thenBy { it.first.name },
            ).map { it.first }
    }

    private fun score(
        query: SearchText,
        candidate: SearchText,
    ): Int =
        when {
            candidate.normalized == query.normalized -> EXACT
            candidate.normalized.startsWith(query.normalized) -> PREFIX
            query.tokens.all { q -> candidate.tokens.any { it.startsWith(q) } } -> ALL_WORDS
            candidate.compact.contains(query.compact) -> CONTAINS
            query.tokens.all { q -> candidate.tokens.any { fuzzyMatches(q, it) } } -> FUZZY
            else -> NONE
        }

    private data class Entry(
        val exercise: Exercise,
        val display: SearchText,
        val others: List<SearchText>,
    )

    private companion object {
        const val EXACT = 100
        const val PREFIX = 90
        const val ALL_WORDS = 80
        const val CONTAINS = 60
        const val FUZZY = 40
        const val NONE = 0
        const val DISPLAY_NAME_BONUS = 5

        val byRankThenName = compareBy<Exercise>({ it.rank ?: Int.MAX_VALUE }, { it.name })
    }
}

/** Search-normalized form of a name: folded, lowercased, tokenized. */
internal class SearchText(
    raw: String,
) {
    val normalized: String = normalizeForSearch(raw)
    val tokens: List<String> = normalized.split(' ').filter { it.isNotEmpty() }
    val compact: String = tokens.joinToString("")
}

internal fun normalizeForSearch(text: String): String {
    val folded = StringBuilder(text.length)
    for (char in text.lowercase()) {
        folded.append(FOLDING[char] ?: char)
    }
    return folded
        .toString()
        // Spelled-out umlauts ("ue" for "ü") fold the same way as the umlauts themselves.
        .replace("ae", "a")
        .replace("oe", "o")
        .replace("ue", "u")
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .replace(WHITESPACE, " ")
}

/**
 * A typo-tolerant token match: [query] is a prefix of [token], or close enough by edit distance. Short
 * words must match exactly, since one typo in a three-letter word is a different word.
 */
internal fun fuzzyMatches(
    query: String,
    token: String,
): Boolean {
    if (token.startsWith(query)) return true
    val allowed =
        when {
            query.length >= LONG_WORD -> 2
            query.length >= MIN_FUZZY_LENGTH -> 1
            else -> return false
        }
    // Compare against the same-length prefix too, so partially typed words with a typo still match.
    val prefix = token.take(query.length)
    return editDistance(query, token, allowed) <= allowed || editDistance(query, prefix, allowed) <= allowed
}

/** Optimal-string-alignment distance (insert, delete, substitute, swap neighbours), capped at [limit] + 1. */
internal fun editDistance(
    a: String,
    b: String,
    limit: Int,
): Int {
    if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
    var previousPrevious = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var rowMin = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            if (isTransposition(a, b, i, j)) value = minOf(value, previousPrevious[j - 2] + 1)
            current[j] = value
            rowMin = minOf(rowMin, value)
        }
        if (rowMin > limit) return limit + 1
        val recycled = previousPrevious
        previousPrevious = previous
        previous = current
        current = recycled
    }
    return previous[b.length]
}

/** Whether a[i-2..i-1] and b[j-2..j-1] are the same two letters swapped ("bnech" vs "bench"). */
private fun isTransposition(
    a: String,
    b: String,
    i: Int,
    j: Int,
): Boolean = i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]

private const val MIN_FUZZY_LENGTH = 4
private const val LONG_WORD = 8
private val WHITESPACE = Regex("\\s+")
private val FOLDING =
    mapOf(
        'ä' to "a",
        'ö' to "o",
        'ü' to "u",
        'ß' to "ss",
        'à' to "a",
        'á' to "a",
        'â' to "a",
        'é' to "e",
        'è' to "e",
        'ê' to "e",
        'í' to "i",
        'ó' to "o",
        'ô' to "o",
        'ú' to "u",
        'ñ' to "n",
        'ç' to "c",
        '°' to "",
    )
