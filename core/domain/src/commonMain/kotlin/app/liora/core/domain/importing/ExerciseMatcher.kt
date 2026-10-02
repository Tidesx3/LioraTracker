package app.liora.core.domain.importing

import app.liora.core.domain.SearchText
import app.liora.core.model.Equipment
import app.liora.core.model.Exercise
import kotlin.math.abs

/** Where an imported exercise's sets go. */
sealed interface ExerciseChoice {
    /** To an exercise that's here already. */
    data class Existing(
        val exerciseId: String,
    ) : ExerciseChoice

    /**
     * To a new custom exercise with the imported name. Based on another exercise, it takes that one's
     * muscles and equipment and becomes a variation of it, so the two stay apart but related.
     */
    data class New(
        val basedOn: String?,
    ) : ExerciseChoice
}

/** What an imported exercise becomes unless the user changes it, and the exercise here it's most like. */
data class ExerciseSuggestion(
    val choice: ExerciseChoice,
    val closest: String?,
)

/**
 * Finds the exercises here that names from another app's export mean. A name is the same exercise
 * when it has the same words as one of an exercise's names or aliases in any language, in any order:
 * "Bench Press (Barbell)" is "Barbell Bench Press", and "Bankdrücken (Langhantel)" is "Bankdrücken".
 * Words for equipment may be added or left out when they agree with the exercise's equipment
 * ("Leg Press (Machine)"), and small words like "am" or "with" don't count.
 *
 * Any other name becomes a new exercise, a variation of the closest one: people name a machine at a
 * particular gym "Leg Press Studio North", and merging it would mix up weights from different machines.
 * When an exercise has no column for what was logged, like weight on a bodyweight pull-up, a variant
 * that has one is used ("Weighted Pull-Up"), or a new exercise tracked the right way.
 */
class ExerciseMatcher(
    exercises: List<Exercise>,
) {
    private val terms =
        exercises.flatMap { exercise ->
            (listOf(exercise.name) + exercise.searchTerms)
                .mapIndexed { index, name -> Term(exercise, matchWords(name), isName = index == 0) }
                .filter { it.words.isNotEmpty() }
        }

    fun suggest(
        name: String,
        fields: SetFields,
    ): ExerciseSuggestion {
        val words = matchWords(name)
        val same = terms.filter { isSame(words, it) }.minWithOrNull(bySameness(words))
        if (same != null) {
            val exercise = same.exercise
            val existing = exercise.takeIf { fields.fit(it.trackingType) } ?: variantOf(same, fields)
            return if (existing != null) {
                ExerciseSuggestion(ExerciseChoice.Existing(existing.id), existing.id)
            } else {
                ExerciseSuggestion(ExerciseChoice.New(basedOn = exercise.id), exercise.id)
            }
        }
        val closest = closest(words)
        return ExerciseSuggestion(ExerciseChoice.New(basedOn = closest?.id), closest?.id)
    }

    /** The same words, give or take small words and words for the exercise's own equipment. */
    private fun isSame(
        words: Set<String>,
        term: Term,
    ): Boolean {
        if ((words intersect term.words).none { it !in WEAK_WORDS }) return false
        return differing(words, term.words).all { it in SMALL_WORDS || EQUIPMENT_WORDS[it] == term.exercise.equipment }
    }

    /** Words in common first, then names before aliases, then common exercises before rare ones. */
    private fun bySameness(words: Set<String>) =
        compareBy<Term>(
            { differing(words, it.words).size },
            { !it.isName },
            { it.exercise.rank ?: Int.MAX_VALUE },
            { it.exercise.name },
        )

    /**
     * An exercise with every word of [same] and a column for everything logged: "Weighted Pull-Up"
     * for weighted pull-ups. Variants that only add "weighted" come first.
     */
    private fun variantOf(
        same: Term,
        fields: SetFields,
    ): Exercise? =
        terms
            .filter { it.exercise.id != same.exercise.id && fields.fit(it.exercise.trackingType) }
            .filter { it.words.containsAll(same.words) }
            .minWithOrNull(
                compareBy<Term>(
                    { !WEIGHTED_WORDS.containsAll(it.words - same.words) },
                    { (it.words - same.words).size },
                    { it.exercise.rank ?: Int.MAX_VALUE },
                    { it.exercise.name },
                ),
            )?.exercise

    /**
     * The built-in exercise whose name shares the most words with [words], by the Dice coefficient:
     * twice the shared words over all words in both, at least a third. A shared word must say what the
     * exercise is, not only its equipment or posture. Custom exercises don't count: their names share
     * words like the gym's ("Leg Press Studio North", "Lat Pulldown Studio North") that say nothing about
     * the movement.
     */
    private fun closest(words: Set<String>): Exercise? =
        terms
            .asSequence()
            .filter { !it.exercise.isCustom }
            .mapNotNull { term -> similarity(words, term)?.let { term.exercise to it } }
            .maxWithOrNull(compareBy { it.second })
            ?.first

    /** How alike [words] and [term] are; null when they aren't. */
    private fun similarity(
        words: Set<String>,
        term: Term,
    ): Score? {
        val shared = words.filter { word -> term.words.any { alike(word, it) } }
        val allWords = words.size + term.words.size
        if (shared.none { it !in WEAK_WORDS } || SIMILARITY_PARTS * 2 * shared.size < allWords) return null
        return Score(
            dice = 2.0 * shared.size / allWords,
            covered = shared.size.toDouble() / term.words.size,
            isName = term.isName,
            rank = term.exercise.rank ?: Int.MAX_VALUE,
        )
    }

    private class Term(
        val exercise: Exercise,
        val words: Set<String>,
        val isName: Boolean,
    )

    private data class Score(
        val dice: Double,
        val covered: Double,
        val isName: Boolean,
        val rank: Int,
    ) : Comparable<Score> {
        override fun compareTo(other: Score): Int =
            compareValuesBy(this, other, { it.dice }, { it.covered }, { it.isName }, { -it.rank })
    }

    private companion object {
        /** Names a third alike are related; below that, a new exercise starts on its own. */
        const val SIMILARITY_PARTS = 3

        val SMALL_WORDS =
            words(
                "a",
                "am",
                "an",
                "and",
                "at",
                "auf",
                "das",
                "de",
                "dem",
                "den",
                "der",
                "des",
                "die",
                "for",
                "im",
                "in",
                "mit",
                "of",
                "on",
                "the",
                "to",
                "und",
                "with",
            )

        val EQUIPMENT_WORDS =
            mapOf(
                "machine" to Equipment.Machine,
                "maschine" to Equipment.Machine,
                "cable" to Equipment.Cable,
                "kabel" to Equipment.Cable,
                "kabelzug" to Equipment.Cable,
                "barbell" to Equipment.Barbell,
                "langhantel" to Equipment.Barbell,
                "dumbbell" to Equipment.Dumbbell,
                "kurzhantel" to Equipment.Dumbbell,
                "kettlebell" to Equipment.Kettlebell,
                "kugelhantel" to Equipment.Kettlebell,
                "band" to Equipment.Band,
                "bodyweight" to Equipment.Bodyweight,
                "korpergewicht" to Equipment.Bodyweight,
            )

        val WEIGHTED_WORDS = words("weighted", "gewichtet")

        /** Words that say how or with what, not what: alone they don't make two names alike. */
        val WEAK_WORDS =
            SMALL_WORDS + EQUIPMENT_WORDS.keys + WEIGHTED_WORDS +
                words("seated", "sitzend", "sitzende", "sitzendes", "standing", "stehend", "grip", "griff")

        /** Words as [matchWords] gives them. */
        fun words(vararg words: String): Set<String> = words.map(::singular).toSet()
    }
}

/** A name's words as matching compares them: searched-for form, English plurals folded ("flyes" is "fly"). */
internal fun matchWords(name: String): Set<String> = SearchText(name).tokens.map(::singular).toSet()

private fun singular(word: String): String =
    when {
        word.length <= SHORT_WORD -> word
        word.endsWith("ies") -> word.removeSuffix("ies") + "y"
        word.endsWith("yes") -> word.removeSuffix("es")
        word.endsWith("s") && !word.endsWith("ss") -> word.removeSuffix("s")
        else -> word
    }

/** The words in only one of [a] and [b]. */
private fun differing(
    a: Set<String>,
    b: Set<String>,
): Set<String> = (a - b) + (b - a)

/** The same word, or one with a short ending added: "wadenpresse" and "wadenpressen". */
private fun alike(
    a: String,
    b: String,
): Boolean =
    a == b ||
        (
            minOf(a.length, b.length) >= MIN_STEM &&
                abs(a.length - b.length) <= MAX_ENDING &&
                (a.startsWith(b) || b.startsWith(a))
        )

/** Words this short keep their final "s": "ups" becomes "up", but "as" stays. */
private const val SHORT_WORD = 2
private const val MIN_STEM = 5
private const val MAX_ENDING = 2
