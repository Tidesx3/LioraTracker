package app.liora.core.data

import app.liora.core.data.update.InstallProgress
import app.liora.core.data.update.LocalUpdateRepository
import app.liora.core.data.update.ReleaseSource
import app.liora.core.data.update.UpdateInstaller
import app.liora.core.data.update.UpdateUnreachableException
import app.liora.core.domain.update.AppVersion
import app.liora.core.domain.update.Release
import app.liora.core.domain.update.ReleaseApk
import app.liora.core.domain.update.UpdateProblem
import app.liora.core.domain.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Updates from GitHub: once a day, Not now, never during a workout, and only in a release build. */
class UpdateRepositoryTest {
    private val phone = TestPhone("a")
    private val source = FakeReleases()
    private val installer = FakeInstaller()

    private fun repository(installer: UpdateInstaller = this.installer) =
        LocalUpdateRepository(phone.database.localMetaDao(), source, installer, phone.workouts, phone.clock)

    @Test
    fun aNewerReleaseIsOfferedAndKeptAcrossARestart() =
        runTest {
            source.latest = release("0.5.0")
            repository().check()
            val available = assertIs<UpdateState.Available>(repository().state.first())
            assertEquals(AppVersion(0, 5, 0), available.release.version)
            assertEquals("- New things", available.release.notes)
            assertEquals("deadbeef", available.release.apk.sha256)
        }

    @Test
    fun theSameOrAnOlderReleaseIsNot() =
        runTest {
            val updates = repository()
            source.latest = release("0.4.0")
            updates.check()
            assertEquals(UpdateState.UpToDate(phone.clock.now), updates.state.first())

            // A release that was newer, checked again after this build caught up, is gone.
            source.latest = release("0.3.9")
            updates.check()
            assertIs<UpdateState.UpToDate>(updates.state.first())
        }

    @Test
    fun itChecksOnceADayAndSoonerAfterAFailedTry() =
        runTest {
            val updates = repository()
            updates.checkIfDue()
            updates.checkIfDue()
            assertEquals(1, source.asked)

            phone.clock.advance(23.hours)
            updates.checkIfDue()
            assertEquals(1, source.asked)
            phone.clock.advance(1.hours)
            updates.checkIfDue()
            assertEquals(2, source.asked)

            // GitHub out of reach: tried again an hour later, not the next day.
            source.unreachable = true
            phone.clock.advance(1.days())
            updates.checkIfDue()
            assertEquals(UpdateState.Unreachable(phone.clock.now - 1.days()), updates.state.first())
            phone.clock.advance(59.minutes)
            updates.checkIfDue()
            assertEquals(3, source.asked)
            phone.clock.advance(1.minutes)
            updates.checkIfDue()
            assertEquals(4, source.asked)
        }

    @Test
    fun turnedOffItOnlyChecksWhenAsked() =
        runTest {
            val updates = repository()
            updates.setAutoCheck(false)
            assertEquals(false, updates.autoCheck.first())
            updates.checkIfDue()
            assertEquals(0, source.asked)
            updates.check()
            assertEquals(1, source.asked)
        }

    @Test
    fun notNowHidesThisVersionUntilANewerOne() =
        runTest {
            val updates = repository()
            source.latest = release("0.5.0")
            updates.check()
            updates.dismiss()
            assertTrue(assertIs<UpdateState.Available>(updates.state.first()).dismissed)

            source.latest = release("0.5.1")
            updates.check()
            val newer = assertIs<UpdateState.Available>(updates.state.first())
            assertEquals(false, newer.dismissed)
        }

    @Test
    fun updatingWaitsForTheWorkoutAndFollowsTheDownload() =
        runTest {
            val updates = repository()
            source.latest = release("0.5.0")
            updates.check()

            phone.workouts.startEmptyWorkout()
            assertTrue(assertIs<UpdateState.Available>(updates.state.first()).duringWorkout)
            updates.update()
            assertEquals(emptyList(), installer.started)
            phone.workouts.discard()

            updates.update()
            assertEquals(listOf(AppVersion(0, 5, 0)), installer.started)
            installer.progress.value = InstallProgress.Downloading(AppVersion(0, 5, 0), 0.4f)
            assertEquals(0.4f, assertIs<UpdateState.Downloading>(updates.state.first()).fraction)
            installer.progress.value = InstallProgress.Ready(AppVersion(0, 5, 0))
            assertIs<UpdateState.ReadyToInstall>(updates.state.first())

            // Ready, but a workout began meanwhile: it installs after.
            phone.workouts.startEmptyWorkout()
            updates.install()
            assertEquals(0, installer.installs)
            phone.workouts.discard()
            updates.install()
            assertEquals(1, installer.installs)

            installer.progress.value = InstallProgress.Failed(AppVersion(0, 5, 0), UpdateProblem.NotGenuine)
            assertEquals(UpdateProblem.NotGenuine, assertIs<UpdateState.Failed>(updates.state.first()).problem)
        }

    @Test
    fun aDebugBuildNeverChecks() =
        runTest {
            val updates = repository(FakeInstaller(enabled = false, versionName = "0.4.0-debug"))
            updates.checkIfDue()
            updates.check()
            assertEquals(0, source.asked)
            assertEquals(UpdateState.Off, updates.state.first())
            assertEquals("0.4.0-debug", updates.versionName)
        }

    private fun release(version: String) =
        Release(
            AppVersion.parse(version)!!,
            notes = "- New things",
            apk = ReleaseApk("https://example.com/liora-$version.apk", sizeBytes = 10_000_000, sha256 = "deadbeef"),
        )

    private fun Int.days() = (this * 24).hours

    private class FakeReleases : ReleaseSource {
        var latest: Release? = null
        var unreachable = false
        var asked = 0

        override suspend fun latest(): Release? {
            asked++
            if (unreachable) throw UpdateUnreachableException("offline")
            return latest
        }
    }

    private class FakeInstaller(
        override val enabled: Boolean = true,
        override val versionName: String = "0.4.0",
    ) : UpdateInstaller {
        override val progress = MutableStateFlow<InstallProgress>(InstallProgress.Idle)
        val started = mutableListOf<AppVersion>()
        var installs = 0

        override fun start(release: Release) {
            started += release.version
        }

        override fun install() {
            installs++
        }
    }
}
