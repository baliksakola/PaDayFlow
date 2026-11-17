package com.padayflow.app.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

data class BackupSummary(
    val taskCount: Int,
    val habitCount: Int,
    val habitLogCount: Int,
    val noteCount: Int
)

data class ParsedBackup(
    val tasks: List<TaskEntity>,
    val habits: List<HabitEntity>,
    val habitLogs: List<HabitLogEntity>,
    val notes: List<NoteEntity>
) {
    val summary = BackupSummary(tasks.size, habits.size, habitLogs.size, notes.size)
}

object BackupManager {
    private const val FORMAT = "padayflow-backup"
    private const val VERSION = 3

    suspend fun createBackup(database: PaDayFlowDatabase): String = database.withTransaction {
        // Read every collection from one Room snapshot so concurrent edits cannot
        // produce a backup containing data from different points in time.
        val tasks = database.taskDao().getAllForBackup()
        val habits = database.habitDao().getAllHabitsForBackup()
        val logs = database.habitDao().getAllLogsForBackup()
        val notes = database.noteDao().getAllForBackup()

        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("tasks", JSONArray().apply {
                tasks.forEach { item -> put(JSONObject()
                    .put("id", item.id).put("title", item.title).put("category", item.category)
                    .put("description", item.description).put("priority", item.priority)
                    .put("status", item.status).put("startAt", item.startAt ?: JSONObject.NULL)
                    .put("deadlineAt", item.deadlineAt ?: JSONObject.NULL)
                    .put("estimatedMinutes", item.estimatedMinutes)
                    .put("reminderAt", item.reminderAt ?: JSONObject.NULL)
                    .put("notes", item.notes).put("createdAt", item.createdAt)
                    .put("scheduledStartAt", item.scheduledStartAt ?: JSONObject.NULL)
                    .put("scheduledEndAt", item.scheduledEndAt ?: JSONObject.NULL)
                    .put("recurrenceRule", item.recurrenceRule)
                    .put("recurrenceInterval", item.recurrenceInterval)
                    .put("recurrenceEndAt", item.recurrenceEndAt ?: JSONObject.NULL)) }
            })
            .put("habits", JSONArray().apply {
                habits.forEach { item -> put(JSONObject().put("id", item.id).put("name", item.name)
                    .put("targetPerWeek", item.targetPerWeek).put("createdAt", item.createdAt)) }
            })
            .put("habitLogs", JSONArray().apply {
                logs.forEach { item -> put(JSONObject().put("habitId", item.habitId)
                    .put("date", item.date).put("completed", item.completed)) }
            })
            .put("notes", JSONArray().apply {
                notes.forEach { item -> put(JSONObject().put("id", item.id).put("title", item.title)
                    .put("content", item.content).put("type", item.type)
                    .put("createdAt", item.createdAt).put("updatedAt", item.updatedAt)) }
            })
        root.toString(2)
    }

    fun parseBackup(json: String): ParsedBackup {
        require(json.length <= 20_000_000) { "Ukuran file backup terlalu besar." }
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "File bukan backup PaDayFlow." }
        val backupVersion = root.optInt("version", -1)
        require(backupVersion in 1..VERSION) { "Versi backup tidak didukung." }

        val tasks = root.getJSONArray("tasks").toList { o ->
            TaskEntity(
                id = o.getLong("id"), title = o.getString("title"),
                category = o.optString("category", "Pribadi"),
                description = o.optString("description", ""),
                priority = o.optString("priority", "Normal"),
                status = o.optString("status", "Belum dikerjakan"),
                startAt = o.nullableLong("startAt"), deadlineAt = o.nullableLong("deadlineAt"),
                estimatedMinutes = o.optInt("estimatedMinutes", 0),
                reminderAt = o.nullableLong("reminderAt"), notes = o.optString("notes", ""),
                createdAt = o.getLong("createdAt"),
                scheduledStartAt = if (backupVersion >= 2) o.nullableLong("scheduledStartAt") else null,
                scheduledEndAt = if (backupVersion >= 2) o.nullableLong("scheduledEndAt") else null,
                recurrenceRule = if (backupVersion >= 3) o.optString("recurrenceRule", "NONE") else "NONE",
                recurrenceInterval = if (backupVersion >= 3) o.optInt("recurrenceInterval", 1).coerceIn(1, 365) else 1,
                recurrenceEndAt = if (backupVersion >= 3) o.nullableLong("recurrenceEndAt") else null
            ).also { require(it.id > 0 && it.title.isNotBlank()) { "Data tugas tidak valid." } }
        }
        val habits = root.getJSONArray("habits").toList { o ->
            HabitEntity(o.getLong("id"), o.getString("name"), o.getInt("targetPerWeek"), o.getLong("createdAt"))
                .also { require(it.id > 0 && it.name.isNotBlank() && it.targetPerWeek in 1..7) { "Data habit tidak valid." } }
        }
        val habitIds = habits.map { it.id }.toSet()
        val logs = root.getJSONArray("habitLogs").toList { o ->
            HabitLogEntity(o.getLong("habitId"), o.getString("date"), o.optBoolean("completed", true))
                .also { require(it.habitId in habitIds && it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Data log habit tidak valid." } }
        }
        val notes = root.getJSONArray("notes").toList { o ->
            NoteEntity(o.getLong("id"), o.getString("title"), o.optString("content", ""),
                o.optString("type", "NOTE"), o.getLong("createdAt"), o.getLong("updatedAt"))
                .also { require(it.id > 0 && it.type in setOf("NOTE", "BRAIN_DUMP")) { "Data catatan tidak valid." } }
        }
        require(tasks.map { it.id }.distinct().size == tasks.size) { "Ada ID tugas ganda." }
        require(habits.map { it.id }.distinct().size == habits.size) { "Ada ID habit ganda." }
        require(notes.map { it.id }.distinct().size == notes.size) { "Ada ID catatan ganda." }
        require(logs.map { it.habitId to it.date }.distinct().size == logs.size) { "Ada log habit ganda." }
        return ParsedBackup(tasks, habits, logs, notes)
    }

    suspend fun restore(database: PaDayFlowDatabase, backup: ParsedBackup) {
        database.withTransaction {
            database.taskDao().clearForRestore()
            database.habitDao().clearLogsForRestore()
            database.habitDao().clearHabitsForRestore()
            database.noteDao().clearForRestore()
            if (backup.tasks.isNotEmpty()) database.taskDao().insertAllForRestore(backup.tasks)
            if (backup.habits.isNotEmpty()) database.habitDao().insertHabitsForRestore(backup.habits)
            if (backup.habitLogs.isNotEmpty()) database.habitDao().insertLogsForRestore(backup.habitLogs)
            if (backup.notes.isNotEmpty()) database.noteDao().insertAllForRestore(backup.notes)
        }
    }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private inline fun <T> JSONArray.toList(transform: (JSONObject) -> T): List<T> =
        List(length()) { index -> transform(getJSONObject(index)) }
}
