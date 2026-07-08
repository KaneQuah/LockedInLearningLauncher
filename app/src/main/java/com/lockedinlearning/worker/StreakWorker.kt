package com.lockedinlearning.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lockedinlearning.data.repository.ProgressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs at midnight to compute and cache the current streak.
 * In v1 the streak is computed on-demand from ProgressRepository;
 * this worker can be extended to send weekly summary notifications.
 */
@HiltWorker
class StreakWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val progressRepository: ProgressRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Compute streak to warm the cache / trigger future notification logic
        progressRepository.computeStreak()
        return Result.success()
    }
}
