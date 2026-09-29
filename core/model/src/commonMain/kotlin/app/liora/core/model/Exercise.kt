package app.liora.core.model

/** An exercise as the app shows it: catalog data merged with the user's per-exercise settings. */
data class Exercise(
    val id: String,
    /** Display name in the requested language, falling back to English, then the stored name. */
    val name: String,
    /** Other names that should find this exercise in search, in any language. */
    val searchTerms: List<String>,
    val trackingType: TrackingType,
    val equipment: Equipment,
    val category: ExerciseCategory,
    val primaryMuscles: Set<Muscle>,
    val secondaryMuscles: Set<Muscle>,
    val instructions: List<String>,
    /** Loadable image URLs (remote for built-ins, local for custom photos), start position first. */
    val imageUrls: List<String>,
    val isCustom: Boolean,
    /** For custom variations: the built-in exercise this one was derived from. */
    val variationOf: String?,
    val notes: String?,
    /** Popularity rank for built-ins (1 = most common); null for rarely used or custom exercises. */
    val rank: Int?,
    val settings: ExerciseSettings,
)

/** Per-user overrides that apply to built-in and custom exercises alike. */
data class ExerciseSettings(
    val stickyNote: String? = null,
    val restWorkingSeconds: Int? = null,
    val restWarmupSeconds: Int? = null,
    val archived: Boolean = false,
)
