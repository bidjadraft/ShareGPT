package com.example.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.db.AppDatabase
import com.example.data.prefs.RetentionPeriod
import com.example.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AutoDeletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val userPrefs = UserPreferencesRepository(applicationContext)
            val retention = userPrefs.retentionPeriodFlow.first()

            val durationMs = retention.durationMs
            if (durationMs != null && durationMs > 0L) {
                val db = AppDatabase.getInstance(applicationContext)
                val cutoff = System.currentTimeMillis() - durationMs
                db.conversationDao().deleteOlderThan(cutoff)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "PromptChat_AutoDeletion_Work"

        fun schedule(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<AutoDeletionWorker>(24, TimeUnit.HOURS)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (_: Exception) {
                // Ignore in testing environments where WorkManager is not initialized
            }
        }
    }
}
