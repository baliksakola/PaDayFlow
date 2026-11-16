package com.padayflow.app

import com.padayflow.app.data.TaskEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class RecurringTaskFactoryTest {
    private val zone = ZoneId.of("UTC")

    private fun millis(date: String, time: String = "00:00"): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun dailyRecurrenceMovesDatesAndPreservesTimes() {
        val task = TaskEntity(
            title = "Review laporan",
            startAt = millis("2026-11-16"),
            deadlineAt = millis("2026-11-17"),
            scheduledStartAt = millis("2026-11-16", "09:00"),
            scheduledEndAt = millis("2026-11-16", "10:30"),
            reminderAt = millis("2026-11-16", "08:30"),
            recurrenceRule = "DAILY",
            recurrenceInterval = 2
        )

        val next = RecurringTaskFactory.next(task, zone, millis("2026-11-16", "07:00"))

        assertNotNull(next)
        assertEquals(millis("2026-11-18"), next!!.startAt)
        assertEquals(millis("2026-11-19"), next.deadlineAt)
        assertEquals(millis("2026-11-18", "09:00"), next.scheduledStartAt)
        assertEquals(millis("2026-11-18", "10:30"), next.scheduledEndAt)
        assertEquals(millis("2026-11-18", "08:30"), next.reminderAt)
        assertEquals("Belum dikerjakan", next.status)
        assertEquals(0L, next.id)
    }

    @Test
    fun weeklyRecurrenceAdvancesByConfiguredInterval() {
        val task = TaskEntity(
            title = "Rapat rutin",
            startAt = millis("2026-11-16"),
            recurrenceRule = "WEEKLY",
            recurrenceInterval = 3
        )

        val next = RecurringTaskFactory.next(task, zone, millis("2026-11-16"))

        assertNotNull(next)
        assertEquals(millis("2026-12-07"), next!!.startAt)
    }

    @Test
    fun monthlyRecurrenceClampsEndOfMonth() {
        val task = TaskEntity(
            title = "Tagihan bulanan",
            startAt = millis("2026-01-31"),
            recurrenceRule = "MONTHLY"
        )

        val next = RecurringTaskFactory.next(task, zone, millis("2026-01-31"))

        assertNotNull(next)
        assertEquals(millis("2026-02-28"), next!!.startAt)
    }

    @Test
    fun recurrenceStopsAfterEndDate() {
        val task = TaskEntity(
            title = "Pemeriksaan",
            startAt = millis("2026-11-16"),
            recurrenceRule = "WEEKLY",
            recurrenceEndAt = millis("2026-11-20")
        )

        assertNull(RecurringTaskFactory.next(task, zone, millis("2026-11-16")))
    }

    @Test
    fun nonRecurringTaskDoesNotCreateNextOccurrence() {
        val task = TaskEntity(
            title = "Sekali saja",
            startAt = millis("2026-11-16"),
            recurrenceRule = "NONE"
        )

        assertNull(RecurringTaskFactory.next(task, zone, millis("2026-11-16")))
    }

    @Test
    fun unsupportedRecurrenceRuleDoesNotCreateNextOccurrence() {
        val task = TaskEntity(
            title = "Invalid recurrence",
            startAt = millis("2026-11-16"),
            recurrenceRule = "YEARLY"
        )

        assertNull(RecurringTaskFactory.next(task, zone, millis("2026-11-16")))
    }
}
