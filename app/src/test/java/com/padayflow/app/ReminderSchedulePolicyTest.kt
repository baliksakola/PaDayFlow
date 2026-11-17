package com.padayflow.app

import com.padayflow.app.data.TaskEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulePolicyTest {
    private val now = 1_800_000_000_000L

    @Test
    fun schedulesFutureReminderForActiveTask() {
        val task = TaskEntity(id = 1, title = "Aktif", reminderAt = now + 60_000L)
        assertTrue(ReminderSchedulePolicy.shouldSchedule(task, now))
    }

    @Test
    fun doesNotScheduleReminderThatHasPassed() {
        val task = TaskEntity(id = 2, title = "Lewat", reminderAt = now - 1L)
        assertFalse(ReminderSchedulePolicy.shouldSchedule(task, now))
    }

    @Test
    fun doesNotScheduleCompletedTask() {
        val task = TaskEntity(
            id = 3, title = "Selesai", status = "Selesai",
            reminderAt = now + 60_000L
        )
        assertFalse(ReminderSchedulePolicy.shouldSchedule(task, now))
    }

    @Test
    fun doesNotScheduleCancelledTask() {
        val task = TaskEntity(
            id = 4, title = "Dibatalkan", status = "Dibatalkan",
            reminderAt = now + 60_000L
        )
        assertFalse(ReminderSchedulePolicy.shouldSchedule(task, now))
    }

    @Test
    fun doesNotScheduleTaskWithoutReminder() {
        val task = TaskEntity(id = 5, title = "Tanpa pengingat")
        assertFalse(ReminderSchedulePolicy.shouldSchedule(task, now))
    }
    @Test
    fun acceptsActionOnlyForCurrentActiveReminder() {
        val task = TaskEntity(
            id = 6, title = "Pengingat aktif", reminderAt = now + 60_000L
        )
        assertTrue(ReminderSchedulePolicy.matchesCurrentReminder(task, now + 60_000L))
        assertFalse(ReminderSchedulePolicy.matchesCurrentReminder(task, now + 120_000L))
        assertFalse(ReminderSchedulePolicy.matchesCurrentReminder(task, -1L))
    }

    @Test
    fun rejectsActionsForCompletedOrCancelledTasks() {
        val completed = TaskEntity(
            id = 7, title = "Selesai", status = "Selesai",
            reminderAt = now + 60_000L
        )
        val cancelled = TaskEntity(
            id = 8, title = "Batal", status = "Dibatalkan",
            reminderAt = now + 60_000L
        )
        assertFalse(ReminderSchedulePolicy.matchesCurrentReminder(completed, now + 60_000L))
        assertFalse(ReminderSchedulePolicy.matchesCurrentReminder(cancelled, now + 60_000L))
    }

    @Test
    fun recoversLatestFutureReminderWhenOldWorkRunsLate() {
        val latestTrigger = now + 180_000L
        val task = TaskEntity(id = 9, title = "Jadwal baru", reminderAt = latestTrigger)
        assertTrue(
            ReminderSchedulePolicy.replacementTrigger(task, now + 60_000L, now) == latestTrigger
        )
    }

    @Test
    fun doesNotRecoverExpiredOrInactiveReminder() {
        val expired = TaskEntity(id = 10, title = "Lewat", reminderAt = now - 1L)
        val completed = TaskEntity(
            id = 11, title = "Selesai", status = "Selesai",
            reminderAt = now + 60_000L
        )
        assertFalse(
            ReminderSchedulePolicy.replacementTrigger(expired, now - 60_000L, now) != null
        )
        assertFalse(
            ReminderSchedulePolicy.replacementTrigger(completed, now, now) != null
        )
    }

}
