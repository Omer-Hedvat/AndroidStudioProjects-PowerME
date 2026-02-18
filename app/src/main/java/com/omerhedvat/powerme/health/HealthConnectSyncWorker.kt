package com.omerhedvat.powerme.health

import android.content.Context
import androidx.work.*
import com.omerhedvat.powerme.data.database.HealthConnectSyncDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker for syncing Health Connect data daily at 6 AM.
 */
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthConnectManager: HealthConnectManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            healthConnectManager.syncHealthData()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "HealthConnectDailySync"

        /**
         * Schedule daily sync at 6 AM.
         */
        fun scheduleDailySync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Calculate initial delay to 6 AM
            val currentTime = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 6)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)

                // If 6 AM has passed today, schedule for tomorrow
                if (timeInMillis <= currentTime) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }

            val initialDelay = calendar.timeInMillis - currentTime

            val dailyWorkRequest = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                dailyWorkRequest
            )
        }
    }
}
