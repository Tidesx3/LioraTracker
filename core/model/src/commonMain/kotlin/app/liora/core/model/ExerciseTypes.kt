package app.liora.core.model

/**
 * How an exercise is measured; decides which columns a set has. Keys are persisted and synced, so
 * they must never change.
 */
enum class TrackingType(
    val key: String,
    val usesWeight: Boolean = false,
    val usesReps: Boolean = false,
    val usesDuration: Boolean = false,
    val usesDistance: Boolean = false,
) {
    WeightReps("weight_reps", usesWeight = true, usesReps = true),
    BodyweightReps("bodyweight_reps", usesReps = true),

    /** Bodyweight plus added load (weighted pull-ups, dips); the weight column is the added load. */
    WeightedBodyweight("weighted_bodyweight", usesWeight = true, usesReps = true),

    /** Machine or band assistance; the weight column is the assistance, so less is harder. */
    AssistedBodyweight("assisted_bodyweight", usesWeight = true, usesReps = true),
    Duration("duration", usesDuration = true),
    DurationWeight("duration_weight", usesWeight = true, usesDuration = true),
    DistanceDuration("distance_duration", usesDuration = true, usesDistance = true),
    WeightDistance("weight_distance", usesWeight = true, usesDistance = true),
    ;

    companion object {
        fun fromKey(key: String): TrackingType? = entries.firstOrNull { it.key == key }
    }
}

enum class SetType(
    val key: String,
) {
    Warmup("warmup"),
    Normal("normal"),
    Drop("drop"),
    Failure("failure"),
    ;

    companion object {
        fun fromKey(key: String): SetType? = entries.firstOrNull { it.key == key }
    }
}

enum class Muscle(
    val key: String,
) {
    Chest("chest"),
    Shoulders("shoulders"),
    Triceps("triceps"),
    Biceps("biceps"),
    Forearms("forearms"),
    Lats("lats"),
    MiddleBack("middle_back"),
    LowerBack("lower_back"),
    Traps("traps"),
    Neck("neck"),
    Abdominals("abdominals"),
    Quadriceps("quadriceps"),
    Hamstrings("hamstrings"),
    Glutes("glutes"),
    Adductors("adductors"),
    Abductors("abductors"),
    Calves("calves"),
    ;

    companion object {
        fun fromKey(key: String): Muscle? = entries.firstOrNull { it.key == key }
    }
}

enum class Equipment(
    val key: String,
) {
    Barbell("barbell"),
    Dumbbell("dumbbell"),
    Kettlebell("kettlebell"),
    Machine("machine"),
    Cable("cable"),
    EzBar("ez_bar"),
    Band("band"),
    Bodyweight("bodyweight"),
    MedicineBall("medicine_ball"),
    ExerciseBall("exercise_ball"),
    FoamRoll("foam_roll"),
    Other("other"),
    ;

    companion object {
        fun fromKey(key: String): Equipment? = entries.firstOrNull { it.key == key }
    }
}

enum class ExerciseCategory(
    val key: String,
) {
    Strength("strength"),
    Powerlifting("powerlifting"),
    OlympicWeightlifting("olympic_weightlifting"),
    Strongman("strongman"),
    Plyometrics("plyometrics"),
    Cardio("cardio"),
    ;

    companion object {
        fun fromKey(key: String): ExerciseCategory? = entries.firstOrNull { it.key == key }
    }
}
