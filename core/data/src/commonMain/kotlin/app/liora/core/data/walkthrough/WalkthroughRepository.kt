package app.liora.core.data.walkthrough

import app.liora.core.data.seed.ExerciseCatalogSeeder
import app.liora.core.database.TransactionRunner
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.model.LocalMetaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether this phone still has the first-launch walkthrough to show. Kept per device in `local_meta`, so
 * each phone decides for itself: a second phone needs the walkthrough's offer to sync most of all.
 */
interface WalkthroughRepository {
    /** True while the walkthrough is to be shown. */
    val pending: Flow<Boolean>

    /**
     * Decides once, the first time it's asked, and says whether the walkthrough is pending. An install
     * that already has a seed version ran an earlier Liora, so it's past the walkthrough; anything else is
     * new. `AppStartup` asks before it first seeds the catalog, so a new install can't pass for an old one.
     */
    suspend fun settle(): Boolean

    suspend fun finish()

    /** Shows it again, as Settings offers. */
    suspend fun showAgain()
}

internal class LocalWalkthroughRepository(
    private val meta: LocalMetaDao,
    private val transactions: TransactionRunner,
) : WalkthroughRepository {
    override val pending: Flow<Boolean> = meta.observe(KEY).map { it == PENDING }.distinctUntilChanged()

    override suspend fun settle(): Boolean =
        transactions.inTransaction {
            val state =
                meta.get(KEY) ?: run {
                    val existingInstall = meta.get(ExerciseCatalogSeeder.KEY_SEED_VERSION) != null
                    (if (existingInstall) DONE else PENDING).also { meta.put(LocalMetaEntity(KEY, it)) }
                }
            state == PENDING
        }

    override suspend fun finish() = meta.put(LocalMetaEntity(KEY, DONE))

    override suspend fun showAgain() = meta.put(LocalMetaEntity(KEY, PENDING))

    private companion object {
        // Kept in local_meta: each phone has its own first start.
        const val KEY = "walkthrough"
        const val PENDING = "pending"
        const val DONE = "done"
    }
}
