package app.liora.core.domain

import app.liora.core.model.PhotoPose
import app.liora.core.model.ProgressPhoto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Picking photos to compare, how far apart they are, and what someone weighed at the time. */
class ProgressPhotosTest {
    private val front1 = photo("f1", LocalDate(2026, 8, 1), PhotoPose.Front)
    private val side1 = photo("s1", LocalDate(2026, 8, 2), PhotoPose.Side)
    private val front2 = photo("f2", LocalDate(2026, 9, 1), PhotoPose.Front)
    private val untagged = photo("u", LocalDate(2026, 9, 15), pose = null)
    private val front3 = photo("f3", LocalDate(2026, 10, 1), PhotoPose.Front)
    private val all = listOf(front3, side1, untagged, front1, front2)

    @Test
    fun firstAndLatestInAPose() {
        assertEquals(PhotoPair(front1, front3), ProgressPhotos.firstAndLatest(all, PhotoPose.Front))
        // Any pose: the very first and the very latest.
        assertEquals(PhotoPair(front1, front3), ProgressPhotos.firstAndLatest(all, pose = null))
        assertNull(ProgressPhotos.firstAndLatest(all, PhotoPose.Side))
        assertNull(ProgressPhotos.firstAndLatest(all, PhotoPose.Back))
    }

    @Test
    fun theOpeningPairIsTheLatestPoseThatHasTwo() {
        // The latest back photo is the only one in its pose, so front it is.
        val back = photo("b", LocalDate(2026, 10, 2), PhotoPose.Back)
        assertEquals(PhotoPair(front1, front3), ProgressPhotos.opening(all + back))
        // No pose with two: the first and latest of all.
        assertEquals(PhotoPair(side1, back), ProgressPhotos.opening(listOf(side1, untagged, back)))
        assertNull(ProgressPhotos.opening(listOf(back)))
    }

    @Test
    fun aPhotoIsComparedWithTheFirstInItsPose() {
        assertEquals(front1, ProgressPhotos.startFor(all, front2))
        // No earlier photo in that pose, or no pose at all: the first before it.
        assertEquals(front1, ProgressPhotos.startFor(all, untagged))
        assertNull(ProgressPhotos.startFor(all, front1))
    }

    @Test
    fun timeApartReadsLikeSpeech() {
        val start = LocalDate(2026, 8, 1)
        assertEquals(TimeApart(0, TimeApart.Scale.Days), ProgressPhotos.apart(start, start))
        assertEquals(TimeApart(13, TimeApart.Scale.Days), ProgressPhotos.apart(start, LocalDate(2026, 8, 14)))
        assertEquals(TimeApart(2, TimeApart.Scale.Weeks), ProgressPhotos.apart(start, LocalDate(2026, 8, 15)))
        assertEquals(TimeApart(9, TimeApart.Scale.Weeks), ProgressPhotos.apart(LocalDate(2026, 10, 9), start))
        assertEquals(TimeApart(2, TimeApart.Scale.Months), ProgressPhotos.apart(start, LocalDate(2026, 10, 10)))
        assertEquals(TimeApart(14, TimeApart.Scale.Months), ProgressPhotos.apart(start, LocalDate(2027, 10, 1)))
    }

    @Test
    fun weightAroundADayLooksBackAWeek() {
        val daily =
            listOf(
                DayValue(LocalDate(2026, 9, 1), 84.6),
                DayValue(LocalDate(2026, 9, 20), 83.4),
                DayValue(LocalDate(2026, 9, 25), 83.0),
            )
        assertEquals(84.6, BodyMeasurements.around(daily, LocalDate(2026, 9, 1))?.value)
        assertEquals(83.4, BodyMeasurements.around(daily, LocalDate(2026, 9, 24))?.value)
        assertEquals(83.0, BodyMeasurements.around(daily, LocalDate(2026, 10, 2))?.value)
        assertNull(BodyMeasurements.around(daily, LocalDate(2026, 9, 15)))
        assertNull(BodyMeasurements.around(daily, LocalDate(2026, 8, 31)))
    }

    private fun photo(
        id: String,
        day: LocalDate,
        pose: PhotoPose?,
    ) = ProgressPhoto(id, day.atStartOfDayIn(TimeZone.UTC), pose, "/photos/$id.jpg")
}
