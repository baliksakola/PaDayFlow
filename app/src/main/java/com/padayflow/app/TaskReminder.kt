package com.padayflow.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.padayflow.app.data.PaDayFlowDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "padayflow_task_reminders"
private const val ACTION_COMPLETE = "com.padayflow.app.REMINDER_COMPLETE"
private const val ACTION_SNOOZE = "com.padayflow.app.REMINDER_SNOOZE"

internal object ReminderScheduler {
    fun schedule(context: Context, taskId: Long, title: String, triggerAt: Long) {
        val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("taskId" to taskId, "title" to title))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("task-reminder-$taskId", androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("task-reminder-$taskId")
    }

    fun snooze(context: Context, taskId: Long, title: String) {
        schedule(context, taskId, title, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
    }
}

internal class TaskReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val taskId = inputData.getLong("taskId", -1L)
        val title = inputData.getString("title") ?: "Tugas PaDayFlow"
        if (taskId <= 0) return Result.failure()
        createReminderChannel(applicationContext)
        val openIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val contentPending = openIntent?.let {
            PendingIntent.getActivity(applicationContext, taskId.toInt(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val completeIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra("taskId", taskId)
        }
        val snoozeIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("taskId", taskId)
            putExtra("title", title)
        }
        val completePending = PendingIntent.getBroadcast(applicationContext, taskId.toInt() + 100000, completeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snoozePending = PendingIntent.getBroadcast(applicationContext, taskId.toInt() + 200000, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pengingat tugas")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .addAction(android.R.drawable.checkbox_on_background, "Selesai", completePending)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Tunda 1 jam", snoozePending)
            .build()
        return try {
            NotificationManagerCompat.from(applicationContext).notify(taskId.toInt(), notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
}

private fun createReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(CHANNEL_ID, "Pengingat tugas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pengingat waktu pengerjaan tugas PaDayFlow"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1L)
        if (taskId <= 0) return
        when (intent.action) {
            ACTION_COMPLETE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        PaDayFlowDatabase.getInstance(context).taskDao().updateStatus(taskId, "Selesai")
                        NotificationManagerCompat.from(context).cancel(taskId.toInt())
                        ReminderScheduler.cancel(context, taskId)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_SNOOZE -> {
                val title = intent.getStringExtra("title") ?: "Tugas PaDayFlow"
                ReminderScheduler.snooze(context, taskId, title)
                NotificationManagerCompat.from(context).cancel(taskId.toInt())
            }
        }
    }
}
