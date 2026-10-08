package app.liora.android.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.liora.core.data.sync.SyncOutcome
import app.liora.core.data.sync.SyncRepository
import app.liora.core.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Syncs with WorkManager, which waits for a network, retries with backoff, and runs even after Liora
 * has closed: twice a day while sync is on, and once whenever asked.
 */
class WorkSyncScheduler(
    context: Context,
) : SyncScheduler {
    private val work = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override val running: Flow<Boolean> =
        combine(
            work.getWorkInfosForUniqueWorkFlow(NOW),
            work.getWorkInfosForUniqueWorkFlow(PERIODIC),
        ) { now, periodic ->
            (now + periodic).any { it.state == WorkInfo.State.RUNNING }
        }

    override fun start() {
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(
                PERIOD_HOURS,
                TimeUnit.HOURS,
            ).setConstraints(online).build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun syncNow() {
        // One waiting or running is enough: it pushes whatever has changed by the time it runs.
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build()
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    override fun stop() {
        work.cancelUniqueWork(NOW)
        work.cancelUniqueWork(PERIODIC)
    }

    private companion object {
        const val NOW = "sync"
        const val PERIODIC = "sync-periodic"
        const val PERIOD_HOURS = 12L
    }
}

/** One sync, as WorkManager runs it. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val sync: SyncRepository by inject()

    override suspend fun doWork(): Result =
        when (sync.sync()) {
            SyncOutcome.Done -> Result.success()

            SyncOutcome.TryLater -> Result.retry()

            // Waits for the user (signing in again, an update); the next periodic run looks again.
            SyncOutcome.Stopped -> Result.failure()
        }
}
