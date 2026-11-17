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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.padayflow.app.data.PaDayFlowDatabase
import com.padayflow.app.data.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "padayflow_task_reminders"
private const val ACTION_COMPLETE = "com.padayflow.app.REMINDER_COMPLETE"
private const val ACTION_SNOOZE = "com.padayflow.app.REMINDER_SNOOZE"

internal object ReminderSchedulePolicy {
    fun shouldSchedule(task: TaskEntity, now: Long): Boolean =
        task.status != "Selesai" && task.status != "Dibatalkan" &&
            task.reminderAt?.let { it > now } == true
}

internal object ReminderScheduler {
    fun schedule(context: Context, taskId: Long, title: String, triggerAt: Long) {
        val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("taskId" to taskId, "title" to title, "expectedTriggerAt" to triggerAt))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("task-reminder-$taskId", androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("task-reminder-$taskId")
    }

    fun reconcile(context: Context, tasks: List<TaskEntity>) {
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            val trigger = task.reminderAt
            if (!ReminderSchedulePolicy.shouldSchedule(task, now)) {
                cancel(context, task.id)
                NotificationManagerCompat.from(context).cancel(task.id.toInt())
            } else {
                trigger?.let { schedule(context, task.id, task.title, it) }
            }
        }
    }
}

internal class TaskReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val taskId = inputData.getLong("taskId", -1L)
        val fallbackTitle = inputData.getString("title") ?: "Tugas PaDayFlow"
        val expectedTriggerAt = inputData.getLong("expectedTriggerAt", -1L)
        if (taskId <= 0 || expectedTriggerAt <= 0L) return Result.failure()
        val task = try {
            runBlocking(Dispatchers.IO) { PaDayFlowDatabase.getInstance(applicationContext).taskDao().getTaskById(taskId) }
        } catch (_: Exception) {
            return Result.retry()
        }
        if (task == null || task.status == "Selesai" || task.status == "Dibatalkan" ||
            task.reminderAt != expectedTriggerAt) {
            NotificationManagerCompat.from(applicationContext).cancel(taskId.toInt())
            return Result.success()
        }
        if (expectedTriggerAt > System.currentTimeMillis()) return Result.retry()
        val title = task.title.ifBlank { fallbackTitle }
        val snoozeMinutes = try {
            runBlocking { applicationContext.dataStore.data.first()[REMINDER_SNOOZE_MINUTES] ?: 60 }
        } catch (_: Exception) { 60 }
        createReminderChannel(applicationContext)
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("openTaskId", taskId)
        }
        val contentPending = PendingIntent.getActivity(
            applicationContext, taskId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val viewPending = PendingIntent.getActivity(
            applicationContext, taskId.toInt() + 300000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val completeIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra("taskId", taskId)
            putExtra("expectedTriggerAt", expectedTriggerAt)
        }
        val snoozeIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("taskId", taskId)
            putExtra("title", title)
            putExtra("snoozeMinutes", snoozeMinutes)
            putExtra("expectedTriggerAt", expectedTriggerAt)
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
            .addAction(android.R.drawable.ic_menu_view, "Lihat Tugas", viewPending)
            .addAction(android.R.drawable.checkbox_on_background, "Selesai", completePending)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Tunda " + if (snoozeMinutes < 60) "$snoozeMinutes menit" else if (snoozeMinutes == 60) "1 jam" else "2 jam", snoozePending)
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
                val expectedTriggerAt = intent.getLongExtra("expectedTriggerAt", -1L)
                if (expectedTriggerAt <= 0L) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val database = PaDayFlowDatabase.getInstance(context)
                        val dao = database.taskDao()
                        val completion = database.withTransaction {
                            val currentTask = dao.getTaskById(taskId)
                            if (currentTask == null || currentTask.reminderAt != expectedTriggerAt ||
                                dao.completeIfReminderMatches(taskId, expectedTriggerAt) != 1) {
                                Triple(false, null, null)
                            } else {
                                val nextTask = RecurringTaskFactory.next(currentTask)
                                val nextId = nextTask?.let { dao.insert(it) }
                                Triple(true, nextTask, nextId)
                            }
                        }
                        val nextTask = completion.second
                        val nextId = completion.third
                        if (nextTask != null && nextId != null) {
                            nextTask.reminderAt?.takeIf { it > System.currentTimeMillis() }?.let {
                                ReminderScheduler.schedule(context, nextId, nextTask.title, it)
                            }
                        }
                        NotificationManagerCompat.from(context).cancel(taskId.toInt())
                        ReminderScheduler.cancel(context, taskId)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_SNOOZE -> {
                val title = intent.getStringExtra("title") ?: "Tugas PaDayFlow"
                val snoozeMinutes = intent.getIntExtra("snoozeMinutes", 60).coerceIn(15, 120)
                val expectedTriggerAt = intent.getLongExtra("expectedTriggerAt", -1L)
                if (expectedTriggerAt <= 0L) return
                val snoozedUntil = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(snoozeMinutes.toLong())
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val updated = PaDayFlowDatabase.getInstance(context).taskDao()
                            .updateReminderAtIfActive(taskId, expectedTriggerAt, snoozedUntil)
                        if (updated == 1) {
                            ReminderScheduler.schedule(context, taskId, title, snoozedUntil)
                            NotificationManagerCompat.from(context).cancel(taskId.toInt())
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

