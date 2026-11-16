package com.padayflow.app

import com.padayflow.app.data.TaskEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeBlockPlannerTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val date = LocalDate.of(2026, 11, 16)

    private fun at(time: String): Long =
        date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun proposalSkipsExistingTaskAndProtectedTime() {
        val existing = TaskEntity(
            id = 1, title = "Rapat", startAt = at("09:00"),
            scheduledStartAt = at("09:00"), scheduledEndAt = at("10:00"),
            estimatedMinutes = 60
        )
        val candidate = TaskEntity(
            id = 2, title = "Susun laporan", estimatedMinutes = 90,
            priority = "Tinggi"
        )

        val result = TimeBlockPlanner.plan(
            date, listOf(existing, candidate), 6,
            "12:00-13:00|Makan", zone
        )

        assertEquals(1, result.proposedSlots.size)
        assertEquals(at("08:00"), result.proposedSlots.first().startAt)
        assertEquals(at("09:30"), result.proposedSlots.first().endAt)
    }

    @Test
    fun detectsOverlappingTaskSchedules() {
        val first = TaskEntity(
            id = 1, title = "Rapat A", startAt = at("09:00"),
            scheduledStartAt = at("09:00"), scheduledEndAt = at("10:30")
        )
        val second = TaskEntity(
            id = 2, title = "Rapat B", startAt = at("09:00"),
            scheduledStartAt = at("10:00"), scheduledEndAt = at("11:00")
        )

        val result = TimeBlockPlanner.plan(date, listOf(first, second), 6, "", zone)

        assertEquals(1, result.conflicts.size)
        assertEquals("Rapat A", result.conflicts.first().first.title)
    }

    @Test
    fun detectsTaskInsideProtectedTime() {
        val task = TaskEntity(
            id = 1, title = "Pekerjaan", startAt = at("12:15"),
            scheduledStartAt = at("12:15"), scheduledEndAt = at("12:45")
        )

        val result = TimeBlockPlanner.plan(date, listOf(task), 6, "12:00-13:00|Makan", zone)

        assertEquals(1, result.protectedTimeConflicts.size)
    }

    @Test
    fun overnightProtectedTimeBlocksMorningUntilSix() {
        val candidate = TaskEntity(id = 2, title = "Tugas pagi", estimatedMinutes = 60)

        val result = TimeBlockPlanner.plan(date, listOf(candidate), 6, "22:00-06:00|Tidur", zone)

        assertEquals(at("08:00"), result.proposedSlots.first().startAt)
    }

    @Test
    fun completedTasksDoNotConsumeCapacityOrBlockSlots() {
        val completed = TaskEntity(
            id = 1, title = "Selesai", startAt = at("08:00"),
            scheduledStartAt = at("08:00"), scheduledEndAt = at("12:00"),
            estimatedMinutes = 240, status = "Selesai"
        )
        val candidate = TaskEntity(id = 2, title = "Tugas", estimatedMinutes = 60)

        val result = TimeBlockPlanner.plan(date, listOf(completed, candidate), 6, "", zone)

        assertEquals(0, result.plannedMinutes)
        assertEquals(at("08:00"), result.proposedSlots.first().startAt)
    }

    @Test
    fun candidateOrderFollowsPriorityThenDeadline() {
        val normal = TaskEntity(id = 1, title = "Normal", estimatedMinutes = 30, priority = "Normal")
        val urgent = TaskEntity(id = 2, title = "Mendesak", estimatedMinutes = 30, priority = "Mendesak")
        val high = TaskEntity(id = 3, title = "Tinggi", estimatedMinutes = 30, priority = "Tinggi")

        val result = TimeBlockPlanner.plan(date, listOf(normal, urgent, high), 6, "", zone)

        assertEquals(listOf("Mendesak", "Tinggi", "Normal"), result.proposedSlots.map { it.task.title })
    }

    @Test
    fun capacityIsClampedAndNeverCreatesOverlappingProposals() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "A", estimatedMinutes = 300),
            TaskEntity(id = 2, title = "B", estimatedMinutes = 300)
        )
        val result = TimeBlockPlanner.plan(date, tasks, 1, "", zone)

        assertTrue(result.proposedSlots.size <= 1)
        assertEquals(60, result.capacityMinutes)
    }
}
