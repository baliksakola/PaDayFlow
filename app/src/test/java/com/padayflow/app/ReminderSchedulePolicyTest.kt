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
}
