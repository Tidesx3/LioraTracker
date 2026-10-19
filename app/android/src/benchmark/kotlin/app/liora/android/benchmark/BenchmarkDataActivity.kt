package app.liora.android.benchmark

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.liora.android.di.ProcessScope
import app.liora.core.data.backup.Backup
import app.liora.core.data.backup.BackupJson
import app.liora.core.data.backup.BackupRepository
import app.liora.core.data.settings.SettingsRepository
import app.liora.core.data.walkthrough.WalkthroughRepository
import app.liora.core.data.workout.ActiveWorkoutRepository
import app.liora.core.database.dao.LocalMetaDao
import app.liora.core.database.model.LocalMetaEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gets a build that measures speed ready to be measured; the benchmarks start it before they measure
 * (benchmark/, `Liora.prepare`). The first time, it restores a year of made-up training ([BenchmarkTraining])
 * through the real restore. Every time, it puts the walkthrough behind, ends a workout left running, and turns
 * Liquid Glass on or off when asked ([EXTRA_GLASS]). It says [READY] when it's done. Only in those builds.
 */
class BenchmarkDataActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { gravity = Gravity.CENTER }
        setContentView(status)
        val glass = intent.extras?.takeIf { it.containsKey(EXTRA_GLASS) }?.getBoolean(EXTRA_GLASS)
        // In the process's scope, so leaving the screen can't stop a restore halfway.
        val preparing = get<ProcessScope>().async { prepare(glass) }
        lifecycleScope.launch {
            preparing.await()
            status.text = READY
        }
    }

    private suspend fun prepare(glass: Boolean?) {
        val meta = get<LocalMetaDao>()
        // The catalog is seeded as the process starts (AppStartup), after the walkthrough has been settled.
        meta.observe(SEED_VERSION).first { it != null }
        get<WalkthroughRepository>().finish()
        if (meta.get(TRAINING) == null) {
            val training = BenchmarkTraining(LocalDate.now(), ZoneId.systemDefault()).backup()
            get<BackupRepository>().restoreFile(training, cacheDir)
            meta.put(LocalMetaEntity(TRAINING, LocalDate.now().toString()))
        }
        get<ActiveWorkoutRepository>().discard()
        if (glass != null) get<SettingsRepository>().update { it.copy(glass = glass) }
    }

    private companion object {
        /** Liquid Glass on (true) or off (false); left as it is without. */
        const val EXTRA_GLASS = "glass"
        const val READY = "Ready to be measured"

        /** Written by `ExerciseCatalogSeeder` once the catalog is in. */
        const val SEED_VERSION = "exercise_seed_version"

        /** When the made-up training was restored. */
        const val TRAINING = "benchmark.training"
    }
}

/**
 * Restores [backup] the way one picked in Settings is: written as a backup file in [dir], read and merged.
 * Returns how many rows it wrote.
 */
internal suspend fun BackupRepository.restoreFile(
    backup: Backup,
    dir: File,
): Int {
    val file = File(dir, "benchmark-training.zip")
    ZipOutputStream(file.outputStream()).use { zip ->
        zip.putNextEntry(ZipEntry("liora.json"))
        zip.write(BackupJson.encode(backup).encodeToByteArray())
        zip.closeEntry()
    }
    return try {
        restore(read(Uri.fromFile(file).toString()))
    } finally {
        file.delete()
    }
}
