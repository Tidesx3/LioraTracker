package app.liora.core.domain

/** How double progression behaves, as chosen in Settings › Progression. The defaults are Liora's own picks. */
data class ProgressionSettings(
    val repAims: RepAims = RepAims.WeakestSet,
    val stepUp: StepUpWhen = StepUpWhen.EverySetAtTop,
    val belowRange: BelowRange = BelowRange.TwiceInARow,
    val lastTime: LastTime = LastTime.SameRoutine,
)

/** What the sets aim for while the weight stays. */
enum class RepAims {
    /** Last time's reps, and one more on the set that did fewest (the first, on a tie): 10, 9, 8 → 10, 9, 9. */
    WeakestSet,

    /** Last time's reps, and one more on the first set below the top: 8, 8, 8 → 9, 8, 8 → 9, 9, 8. */
    FirstBelowTop,

    /** Last time's reps again; beating them is up to the day. */
    SameAsLastTime,

    /** One more on every set: 8, 8, 8 → 9, 9, 9. */
    EverySet,
}

/** When the weight goes up a step. */
enum class StepUpWhen {
    /** Every working set reached the top of its range. */
    EverySetAtTop,

    /** The working sets' reps add up to the top of their ranges, so 13, 12, 11 counts on 3 × 8–12. */
    TotalReps,

    /** The first working set reached the top; the others catch up at the new weight. */
    FirstSetAtTop,
}

/** What a set below the bottom of its range does. */
enum class BelowRange {
    /** Two sessions in a row below it take the weight down a step; one is just a bad day. */
    TwiceInARow,

    /** One session below it takes the weight down a step. */
    RightAway,

    /** The weight never goes down by itself. */
    Never,
}

/** Which earlier session progression goes on. */
enum class LastTime {
    /** The exercise's last session in a workout from the same routine; without one, its latest anywhere. */
    SameRoutine,

    /** The exercise's latest session in any workout. */
    Anywhere,
}
