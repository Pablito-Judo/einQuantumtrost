package de.worldpulse.app.scout

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.worldpulse.app.WorldPulseApp
import java.util.concurrent.TimeUnit

/**
 * Leicht-Hintergrund (SPEC 4): Scout ohne LLM per WorkManager, nur im WLAN (unmetered).
 * Laden als Bedingung ist für den Prototyp aus, damit man tagsüber Ergebnisse sieht (später einstellbar).
 */
class ScoutWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WorldPulseApp
        val force = inputData.getBoolean(KEY_FORCE, false)
        return try {
            app.scout.runOnce(force)
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_FORCE = "force"
        private const val PERIODIC = "scout-periodic"
        private const val NOW = "scout-now"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ScoutWorker>(30, TimeUnit.MINUTES).setConstraints(constraints).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<ScoutWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_FORCE to true)).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        fun observeNow(ctx: Context) = WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(NOW)
    }
}
