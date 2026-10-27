package com.example.pary.notify

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.pary.data.GroupRepository
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Фоновая проверка расписания: раз в час (когда есть интернет) скачивает расписание выбранной группы
 * и, если что-то изменилось, присылает уведомление, а виджеты и напоминания пересчитываются.
 * Так о замене узнаёшь вовремя, а не когда откроешь приложение.
 */
object ScheduleSync {
    private const val WORK_NAME = "schedule_sync"
    private const val TAG = "ScheduleSync"
    private const val QUIET_FROM_HOUR = 23
    private const val QUIET_UNTIL_HOUR = 6

    /** Ставит периодическую проверку; повторный вызов её не дублирует. */
    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Ночью с 23 до 6 не проверяем: расписание тогда не меняют, а телефон лучше не будить. */
    fun isQuietHour(hour: Int): Boolean = hour >= QUIET_FROM_HOUR || hour < QUIET_UNTIL_HOUR

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (isQuietHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) return Result.success()
            val context = applicationContext
            val group = GroupRepository(context).selectedGroup.first() ?: return Result.success()
            try {
                val fresh = ScheduleRepository(context).refresh(group.slug, group.subgroup)
                if (fresh.changes.isNotEmpty() && SettingsRepository(context).current().changeAlertsEnabled) {
                    ChangeNotifier.post(context, group.name, fresh.changes)
                }
                onScheduleChanged(context)
                Log.i(TAG, "проверка прошла, изменений: ${fresh.changes.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Нет сети или сайт не ответил: следующая проверка через час.
                Log.i(TAG, "проверка не удалась: ${e.javaClass.simpleName}")
            }
            return Result.success()
        }
    }
}
