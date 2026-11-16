package com.padayflow.app

import com.padayflow.app.data.TaskEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ProposedTaskSlot(val task: TaskEntity, val startAt: Long, val endAt: Long, val rationale: String, val deadlineNote: String)
internal data class TaskTimeConflict(val first: TaskEntity, val second: TaskEntity)
internal data class TimelineInterval(val startAt: Long, val endAt: Long, val label: String)
internal data class DailyPlanResult(
    val todayTasks: List<TaskEntity>,
    val candidates: List<TaskEntity>,
    val proposedSlots: List<ProposedTaskSlot>,
    val conflicts: List<TaskTimeConflict>,
    val protectedTimeConflicts: List<TaskEntity>,
    val protectedSlots: List<TimelineInterval>,
    val freeSlots: List<TimelineInterval>,
    val plannedMinutes: Int,
    val capacityMinutes: Int
) {
    val remainingMinutes: Int get() = (capacityMinutes - plannedMinutes).coerceAtLeast(0)
}

/**
 * Pure time-block calculation. It never mutates tasks; callers must obtain user approval
 * before persisting any proposed slot.
 */
internal object TimeBlockPlanner {
    private data class Interval(val start: Long, val end: Long)

    fun plan(
        date: LocalDate,
        tasks: List<TaskEntity>,
        productiveHours: Int,
        protectedTimes: String,
        zone: ZoneId = ZoneId.systemDefault(),
        dayStart: LocalTime = LocalTime.of(8, 0),
        dayEnd: LocalTime = LocalTime.of(20, 0)
    ): DailyPlanResult {
        val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun belongsToDate(timestamp: Long?): Boolean = timestamp?.let { it >= startOfDay && it < endOfDay } == true

        val active = tasks.filter { it.status != "Selesai" && it.status != "Dibatalkan" }
        val todayTasks = active.filter { belongsToDate(it.startAt) || belongsToDate(it.scheduledStartAt) }
        // Unfinished tasks whose planned start date has passed are eligible for a new proposal.
        // Their original date is preserved until the user approves the replacement slot.
        val candidates = active.filter {
            it.scheduledStartAt == null &&
                (it.startAt == null || belongsToDate(it.startAt) ||
                    (it.startAt < startOfDay && !belongsToDate(it.startAt)))
        }
            .sortedWith(compareBy<TaskEntity>(
                { when (it.priority) { "Mendesak" -> 0; "Tinggi" -> 1; "Normal" -> 2; else -> 3 } },
                { it.deadlineAt ?: Long.MAX_VALUE },
                { it.createdAt }
            ))

        val scheduled = todayTasks.mapNotNull { task ->
            val start = task.scheduledStartAt
            val end = task.scheduledEndAt
            if (start != null && end != null && end > start && belongsToDate(start)) task to Interval(start, end) else null
        }
        val conflicts = mutableListOf<TaskTimeConflict>()
        for (i in scheduled.indices) {
            for (j in i + 1 until scheduled.size) {
                val (first, firstInterval) = scheduled[i]
                val (second, secondInterval) = scheduled[j]
                if (firstInterval.start < secondInterval.end && firstInterval.end > secondInterval.start) {
                    conflicts += TaskTimeConflict(first, second)
                }
            }
        }

        val dayStartAt = date.atTime(dayStart).atZone(zone).toInstant().toEpochMilli()
        val dayEndAt = date.atTime(dayEnd).atZone(zone).toInstant().toEpochMilli()
        val protected = parseProtectedIntervals(date, protectedTimes, zone, dayStartAt, dayEndAt)
        val protectedConflicts = scheduled.filter { (_, interval) ->
            protected.any { interval.start < it.end && interval.end > it.start }
        }.map { it.first }.distinctBy { it.id }

        val occupied = (scheduled.map { it.second } + protected).toMutableList()
        val protectedSlots = protected.map { interval ->
            TimelineInterval(interval.start, interval.end, "Protected Time")
        }.distinctBy { it.startAt to it.endAt }.sortedBy { it.startAt }
        val mergedOccupied = occupied.sortedBy { it.start }.fold(mutableListOf<Interval>()) { merged, interval ->
            val last = merged.lastOrNull()
            if (last == null || interval.start > last.end) merged += interval
            else merged[merged.lastIndex] = Interval(last.start, maxOf(last.end, interval.end))
            merged
        }
        val freeSlots = mutableListOf<TimelineInterval>()
        var freeCursor = dayStartAt
        for (interval in mergedOccupied) {
            val clippedStart = interval.start.coerceAtLeast(dayStartAt)
            val clippedEnd = interval.end.coerceAtMost(dayEndAt)
            if (clippedStart > freeCursor) freeSlots += TimelineInterval(freeCursor, clippedStart, "Waktu kosong")
            freeCursor = maxOf(freeCursor, clippedEnd)
        }
        if (freeCursor < dayEndAt) freeSlots += TimelineInterval(freeCursor, dayEndAt, "Waktu kosong")
        val plannedMinutes = todayTasks.sumOf { it.estimatedMinutes.coerceAtLeast(0) }
        val capacityMinutes = productiveHours.coerceIn(1, 16) * 60
        val proposed = mutableListOf<ProposedTaskSlot>()
        var proposedMinutes = 0
        var cursor = dayStartAt
        // Today's date-only tasks are already included in plannedMinutes. Add their
        // durations back to the proposal budget so scheduling them does not double-count.
        val alreadyCountedCandidateMinutes = candidates
            .filter { candidate -> todayTasks.any { it.id == candidate.id } }
            .sumOf { it.estimatedMinutes.coerceAtLeast(0) }
        val proposalBudget = (capacityMinutes - plannedMinutes + alreadyCountedCandidateMinutes)
            .coerceAtLeast(0)

        for (task in candidates) {
            val duration = task.estimatedMinutes
            if (duration <= 0 || proposedMinutes + duration > proposalBudget) continue
            val durationMillis = duration * 60_000L
            var slotStart = cursor
            while (slotStart + durationMillis <= dayEndAt) {
                val collision = occupied.filter { slotStart < it.end && slotStart + durationMillis > it.start }
                    .minByOrNull { it.end }
                if (collision == null) break
                slotStart = collision.end
            }
            if (slotStart + durationMillis <= dayEndAt) {
                val slotEnd = slotStart + durationMillis
                val priorityReason = when (task.priority) {
                    "Mendesak" -> "Prioritas mendesak"
                    "Tinggi" -> "Prioritas tinggi"
                    "Rendah" -> "Prioritas rendah"
                    else -> "Prioritas normal"
                }
                val deadlineNote = task.deadlineAt?.let { deadline ->
                    val deadlineDate = java.time.Instant.ofEpochMilli(deadline).atZone(zone).toLocalDate()
                    val days = java.time.temporal.ChronoUnit.DAYS.between(date, deadlineDate)
                    when {
                        days < 0 -> "Deadline terlambat ${-days} hari"
                        days == 0L -> "Deadline hari ini"
                        days == 1L -> "Deadline besok"
                        else -> "Deadline ${days} hari lagi"
                    }
                } ?: "Tanpa deadline"
                proposed += ProposedTaskSlot(task, slotStart, slotEnd, priorityReason, deadlineNote)
                occupied += Interval(slotStart, slotEnd)
                cursor = slotEnd
                proposedMinutes += duration
            }
        }

        return DailyPlanResult(
            todayTasks = todayTasks,
            candidates = candidates,
            proposedSlots = proposed,
            conflicts = conflicts,
            protectedTimeConflicts = protectedConflicts,
            protectedSlots = protectedSlots,
            freeSlots = freeSlots,
            plannedMinutes = plannedMinutes,
            capacityMinutes = capacityMinutes
        )
    }

    private fun parseProtectedIntervals(
        date: LocalDate,
        raw: String,
        zone: ZoneId,
        dayStart: Long,
        dayEnd: Long
    ): List<Interval> {
        val result = mutableListOf<Interval>()
        for (offset in -1L..0L) {
            val blockDate = date.plusDays(offset)
            raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split("|")
                val times = parts.firstOrNull()?.split("-") ?: emptyList()
                if (parts.size != 2 || times.size != 2) return@forEach
                val startTime = runCatching { LocalTime.parse(times[0].trim()) }.getOrNull() ?: return@forEach
                val endTime = runCatching { LocalTime.parse(times[1].trim()) }.getOrNull() ?: return@forEach
                if (startTime == endTime) return@forEach
                val start = blockDate.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
                val endDate = if (endTime <= startTime) blockDate.plusDays(1) else blockDate
                val end = endDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
                if (end > dayStart && start < dayEnd) result += Interval(start, end)
            }
        }
        return result
    }
}
