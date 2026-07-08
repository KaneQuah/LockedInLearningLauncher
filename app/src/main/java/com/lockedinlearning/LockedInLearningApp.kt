package com.lockedinlearning

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.lockedinlearning.worker.StreakWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class LockedInLearningApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleStreakWorker()
    }

    private fun scheduleStreakWorker() {
        val request = PeriodicWorkRequestBuilder<StreakWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(computeDelayUntilMidnight(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "streak_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun computeDelayUntilMidnight(): Long {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis - now).coerceAtLeast(0)
    }
}
