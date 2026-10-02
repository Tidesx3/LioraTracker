package app.liora.core.domain

import app.liora.core.model.PhotoPose
import app.liora.core.model.ProgressPhoto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil

/** Two photos side by side: the earlier one first. */
data class PhotoPair(
    val before: ProgressPhoto,
    val after: ProgressPhoto,
)

/** How far apart two days are, in the unit a person would say it: days, then weeks, then months. */
data class TimeApart(
    val count: Int,
    val scale: Scale,
) {
    enum class Scale { Days, Weeks, Months }
}

/** Choosing what to compare: like with like, first with latest. */
object ProgressPhotos {
    /** The first and latest photo in [pose] (any pose when null); null with fewer than two. */
    fun firstAndLatest(
        photos: List<ProgressPhoto>,
        pose: PhotoPose?,
    ): PhotoPair? {
        val matching = photos.filter { pose == null || it.pose == pose }.sortedBy { it.takenAt }
        return if (matching.size < 2) null else PhotoPair(matching.first(), matching.last())
    }

    /**
     * What to compare first: the first and latest photo in the pose photographed most recently, among
     * poses with two photos or more; the first and latest of all photos when no pose has two.
     */
    fun opening(photos: List<ProgressPhoto>): PhotoPair? {
        val pose =
            photos
                .sortedByDescending { it.takenAt }
                .firstNotNullOfOrNull { photo -> photo.pose?.takeIf { firstAndLatest(photos, it) != null } }
        return firstAndLatest(photos, pose)
    }

    /**
     * What to put beside [after]: the first photo before it in the same pose, or the first before it
     * at all when there's none in that pose. Null when [after] is the first photo.
     */
    fun startFor(
        photos: List<ProgressPhoto>,
        after: ProgressPhoto,
    ): ProgressPhoto? {
        val earlier = photos.filter { it.takenAt < after.takenAt }.sortedBy { it.takenAt }
        return earlier.firstOrNull { after.pose != null && it.pose == after.pose } ?: earlier.firstOrNull()
    }

    /** Under two weeks in days, under ten weeks in weeks, then in whole months. */
    fun apart(
        from: LocalDate,
        to: LocalDate,
    ): TimeApart {
        val (start, end) = if (from <= to) from to to else to to from
        val days = start.daysUntil(end)
        return when {
            days < WEEKS_FROM_DAYS -> TimeApart(days, TimeApart.Scale.Days)
            days < MONTHS_FROM_DAYS -> TimeApart(days / DAYS_PER_WEEK, TimeApart.Scale.Weeks)
            else -> TimeApart(start.monthsUntil(end), TimeApart.Scale.Months)
        }
    }

    private const val DAYS_PER_WEEK = 7
    private const val WEEKS_FROM_DAYS = 14
    private const val MONTHS_FROM_DAYS = 70
}
