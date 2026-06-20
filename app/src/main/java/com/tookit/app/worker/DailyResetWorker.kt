package com.tookit.app.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tookit.app.data.repository.MedicineRepository
import com.tookit.app.widget.TookItWidget
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = MedicineRepository(applicationContext)
        repo.resetAllTakenStates()

        // Refresh all widget instances
        GlanceAppWidgetManager(applicationContext)
            .getGlanceIds(TookItWidget::class.java)
            .forEach { glanceId ->
                TookItWidget().refresh(applicationContext, glanceId)
            }

        // Schedule next reset
        val config = repo.getConfig()
        scheduleNext(applicationContext, config.dailyResetTime)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_reset"

        suspend fun schedule(context: Context) {
            val resetTime = MedicineRepository(context).getConfig().dailyResetTime
            schedule(context, resetTime)
        }

        fun schedule(context: Context, resetTime: LocalTime) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            scheduleNext(context, resetTime)
        }

        private fun scheduleNext(context: Context, resetTime: LocalTime) {
            val now = LocalDateTime.now()
            var nextReset = now.withHour(resetTime.hour).withMinute(resetTime.minute).withSecond(0)
            if (!nextReset.isAfter(now)) {
                nextReset = nextReset.plusDays(1)
            }
            val delay = Duration.between(now, nextReset).toMillis()

            val request = OneTimeWorkRequestBuilder<DailyResetWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
