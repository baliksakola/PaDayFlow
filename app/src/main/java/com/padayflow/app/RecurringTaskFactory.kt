package com.padayflow.app

import com.padayflow.app.data.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Creates the next occurrence only after the current recurring task is completed.
 * All date calculations use the device's current time zone.
 */
internal object RecurringTaskFactory {
    fun next(task: TaskEntity, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): TaskEntity? {
        if (task.recurrenceRule == "NONE") return null
        val baseDate = task.startAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val interval = task.recurrenceInterval.coerceIn(1, 365)
        val nextDate = when (task.recurrenceRule) {
            "DAILY" -> baseDate.plusDays(interval.toLong())
            "WEEKLY" -> baseDate.plusWeeks(interval.toLong())
            "MONTHLY" -> {
                val targetMonth = baseDate.plusMonths(interval.toLong())
                if (baseDate.dayOfMonth == baseDate.lengthOfMonth()) {
                    targetMonth.withDayOfMonth(targetMonth.lengthOfMonth())
                } else {
                    targetMonth.withDayOfMonth(minOf(baseDate.dayOfMonth, targetMonth.lengthOfMonth()))
                }
            }
            else -> return null
        }
        val nextStart = nextDate.atStartOfDay(zone).toInstant().toEpochMilli()
        if (task.recurrenceEndAt != null && nextStart > task.recurrenceEndAt) return null

        val oldDeadlineDate = task.deadlineAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val deadlineDate = oldDeadlineDate?.let {
            nextDate.plusDays(ChronoUnit.DAYS.between(baseDate, it))
        }
        fun shiftTime(value: Long?): Long? = value?.let {
            val time = Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
            nextDate.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }

        return task.copy(
            id = 0,
            status = "Belum dikerjakan",
            startAt = nextStart,
            deadlineAt = deadlineDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            scheduledStartAt = shiftTime(task.scheduledStartAt),
            scheduledEndAt = shiftTime(task.scheduledEndAt),
            reminderAt = shiftTime(task.reminderAt),
            createdAt = now
        )
    }
}
