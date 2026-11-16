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
        assertEquals(at("10:00"), result.proposedSlots.first().startAt)
        assertEquals(at("11:30"), result.proposedSlots.first().endAt)
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
    fun overdueUnfinishedTaskBecomesRescheduleCandidateWithoutMutatingOriginal() {
        val yesterday = date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val overdue = TaskEntity(
            id = 10, title = "Tugas tertunda", startAt = yesterday,
            estimatedMinutes = 45, status = "Tertunda"
        )
        val result = TimeBlockPlanner.plan(date, listOf(overdue), 6, "", zone)

        assertTrue(result.candidates.any { it.id == overdue.id })
        assertTrue(result.todayTasks.none { it.id == overdue.id })
        assertEquals(yesterday, overdue.startAt)
        assertEquals(1, result.proposedSlots.size)
    }

    @Test
    fun scheduledTaskWithoutDateOnlyStartIsCountedInTodayPlan() {
        val scheduled = TaskEntity(
            id = 9, title = "Jadwal dari time block",
            scheduledStartAt = at("10:00"), scheduledEndAt = at("11:00"),
            estimatedMinutes = 60
        )
        val result = TimeBlockPlanner.plan(date, listOf(scheduled), 6, "", zone)
        assertEquals(1, result.todayTasks.size)
        assertEquals(60, result.plannedMinutes)
        assertTrue(result.candidates.isEmpty())
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
    fun proposalsRespectConfiguredWorkdayRange() {
        val candidate = TaskEntity(id = 7, title = "Tugas sore", estimatedMinutes = 60)
        val result = TimeBlockPlanner.plan(
            date, listOf(candidate), 6, "", zone,
            LocalTime.of(10, 0), LocalTime.of(16, 0)
        )
        assertEquals(at("10:00"), result.proposedSlots.first().startAt)
        assertEquals(at("11:00"), result.proposedSlots.first().endAt)
    }

    @Test
    fun proposalDoesNotExtendPastConfiguredWorkdayEnd() {
        val candidate = TaskEntity(id = 8, title = "Tugas panjang", estimatedMinutes = 120)
        val result = TimeBlockPlanner.plan(
            date, listOf(candidate), 6, "", zone,
            LocalTime.of(15, 0), LocalTime.of(16, 0)
        )
        assertTrue(result.proposedSlots.isEmpty())
    }

    @Test
    fun exposesProtectedIntervalsAndFreeGapsWithoutOverlap() {
        val task = TaskEntity(
            id = 21, title = "Rapat",
            startAt = at("09:00"), scheduledStartAt = at("09:00"),
            scheduledEndAt = at("10:00"), estimatedMinutes = 60
        )
        val result = TimeBlockPlanner.plan(
            date, listOf(task), 6, "12:00-13:00|Makan", zone,
            LocalTime.of(8, 0), LocalTime.of(17, 0)
        )

        assertEquals(1, result.protectedSlots.size)
        assertEquals(at("12:00"), result.protectedSlots.first().startAt)
        assertTrue(result.freeSlots.any { it.startAt == at("10:00") && it.endAt == at("12:00") })
        assertTrue(result.freeSlots.none { gap ->
            gap.startAt < at("10:00") && gap.endAt > at("09:00")
        })
    }

    @Test
    fun overloadedDayReportsZeroRemainingCapacity() {
        val task = TaskEntity(id = 22, title = "Pekerjaan", startAt = at("08:00"), estimatedMinutes = 180)
        val result = TimeBlockPlanner.plan(date, listOf(task), 2, "", zone)
        assertEquals(0, result.remainingMinutes)
        assertEquals(120, result.capacityMinutes)
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
    @Test
    fun proposalExplainsPriorityAndDeadlineUrgency() {
        val urgent = TaskEntity(
            id = 31, title = "Laporan mendesak", estimatedMinutes = 45,
            priority = "Mendesak", deadlineAt = at("00:00") + 24 * 60 * 60 * 1000L
        )
        val result = TimeBlockPlanner.plan(date, listOf(urgent), 6, "", zone)
        val proposal = result.proposedSlots.single()

        assertEquals("Prioritas mendesak", proposal.rationale)
        assertEquals("Deadline besok", proposal.deadlineNote)
    }

    @Test
    fun proposalFlagsOverdueDeadlineInItsExplanation() {
        val overdue = TaskEntity(
            id = 32, title = "Revisi", estimatedMinutes = 30,
            deadlineAt = date.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        )
        val result = TimeBlockPlanner.plan(date, listOf(overdue), 6, "", zone)

        assertEquals("Deadline terlambat 2 hari", result.proposedSlots.single().deadlineNote)
    }

    @Test
    fun todayDateOnlyTaskBecomesTimeBlockCandidateWithoutDoubleCountingCapacity() {
        val todayTask = TaskEntity(
            id = 41, title = "Buat laporan", startAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            estimatedMinutes = 90, priority = "Tinggi"
        )
        val result = TimeBlockPlanner.plan(date, listOf(todayTask), 2, "", zone)

        assertEquals(1, result.todayTasks.size)
        assertEquals(1, result.candidates.size)
        assertEquals(1, result.proposedSlots.size)
        assertEquals(todayTask.id, result.proposedSlots.single().task.id)
        assertEquals(at("08:00"), result.proposedSlots.single().startAt)
        assertEquals(90, result.plannedMinutes)
        assertEquals(30, result.remainingMinutes)
    }

    @Test
    fun todayDateOnlyTaskProposalStillRespectsOtherScheduledBlocks() {
        val fixed = TaskEntity(
            id = 42, title = "Rapat", startAt = at("09:00"),
            scheduledStartAt = at("09:00"), scheduledEndAt = at("10:00"),
            estimatedMinutes = 60
        )
        val flexible = TaskEntity(
            id = 43, title = "Administrasi", startAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
            estimatedMinutes = 60
        )
        val result = TimeBlockPlanner.plan(date, listOf(fixed, flexible), 4, "", zone)

        assertEquals(at("08:00"), result.proposedSlots.single().startAt)
        assertEquals(120, result.plannedMinutes)
    }

}
