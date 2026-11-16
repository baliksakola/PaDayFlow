package com.padayflow.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.padayflow.app.data.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProposedTaskSlot(val task: TaskEntity, val startAt: Long, val endAt: Long)

@Composable
internal fun DailyPlanningScreen(
    tasks: List<TaskEntity>,
    productiveHours: Int,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onApproveSchedule: (TaskEntity) -> Unit
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val active = tasks.filter { it.status != "Selesai" && it.status != "Dibatalkan" }
    val todayTasks = active.filter { it.startAt != null && it.startAt >= startOfToday && it.startAt < endOfToday }
    val plannedMinutes = todayTasks.sumOf { it.estimatedMinutes.coerceAtLeast(0) }
    val capacityMinutes = productiveHours.coerceIn(1, 16) * 60
    val remainingMinutes = (capacityMinutes - plannedMinutes).coerceAtLeast(0)
    val candidates = active.filter { it.startAt == null }
        .sortedWith(compareBy<TaskEntity>(
            { when (it.priority) { "Mendesak" -> 0; "Tinggi" -> 1; "Normal" -> 2; else -> 3 } },
            { it.deadlineAt ?: Long.MAX_VALUE },
            { it.createdAt }
        ))
    val existingSlots = todayTasks.mapNotNull { task ->
        val start = task.scheduledStartAt
        val end = task.scheduledEndAt
        if (start != null && end != null && end > start) start to end else null
    }.toMutableList()
    val proposedSlots = mutableListOf<ProposedTaskSlot>()
    var proposedMinutes = 0
    val dayStart = today.atTime(LocalTime.of(8, 0)).atZone(zone).toInstant().toEpochMilli()
    val dayEnd = today.atTime(LocalTime.of(20, 0)).atZone(zone).toInstant().toEpochMilli()
    var cursor = dayStart
    for (task in candidates) {
        val duration = task.estimatedMinutes.coerceAtLeast(0)
        if (duration <= 0 || proposedMinutes + duration > remainingMinutes) continue
        val durationMillis = duration * 60_000L
        var slotStart = cursor
        while (slotStart + durationMillis <= dayEnd) {
            val collision = existingSlots.firstOrNull { (start, end) ->
                slotStart < end && slotStart + durationMillis > start
            }
            if (collision == null) break
            slotStart = collision.second
        }
        if (slotStart + durationMillis <= dayEnd) {
            val slotEnd = slotStart + durationMillis
            proposedSlots += ProposedTaskSlot(task, slotStart, slotEnd)
            existingSlots += slotStart to slotEnd
            cursor = slotEnd
            proposedMinutes += duration
        }
    }
    val proposed = proposedSlots.map { it.task }
    val dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))
        .replaceFirstChar { it.uppercase(Locale("id", "ID")) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }
            Column(Modifier.weight(1f)) {
                Text("Daily Planning", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(dateLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kapasitas hari ini", fontWeight = FontWeight.SemiBold)
                Text("${plannedMinutes / 60} jam ${plannedMinutes % 60} menit dari ${productiveHours} jam",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { (plannedMinutes.toFloat() / capacityMinutes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(remainingMinutes / 60)} jam ${remainingMinutes % 60} menit tersisa · ${todayTasks.size} tugas terjadwal",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        val conflicts = todayTasks.filter { first -> first.scheduledStartAt != null && first.scheduledEndAt != null }.filter { first ->
            todayTasks.any { second -> second.id != first.id && second.scheduledStartAt != null && second.scheduledEndAt != null &&
                first.scheduledStartAt!! < second.scheduledEndAt!! && first.scheduledEndAt!! > second.scheduledStartAt!! }
        }.distinctBy { it.id }
        if (conflicts.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Konflik jadwal terdeteksi", fontWeight = FontWeight.SemiBold)
                    conflicts.forEach { Text(it.title, style = MaterialTheme.typography.bodySmall) }
                    Text("Buka dan ubah jam salah satu tugas agar tidak bertumpuk.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Usulan penjadwalan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Tugas tanpa tanggal mulai diurutkan berdasarkan prioritas dan deadline. Tidak ada jadwal yang diubah sebelum disetujui.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (proposed.isEmpty()) {
            Text(
                if (candidates.isEmpty()) "Tidak ada tugas tanpa jadwal." else "Tidak ada kandidat dengan estimasi durasi yang sesuai kapasitas tersisa. Periksa tugas tanpa estimasi atau kurangi beban hari ini.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            proposed.forEach { task ->
                Card(Modifier.fillMaxWidth().clickable { onOpenTask(task) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val slot = proposedSlots.first { it.task.id == task.id }
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text("${Instant.ofEpochMilli(slot.startAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))}–${Instant.ofEpochMilli(slot.endAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${task.priority} · ${task.estimatedMinutes} menit")
                        Text("${task.priority} · ${task.estimatedMinutes} menit" +
                            (task.deadlineAt?.let { " · Deadline ${Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { onApproveSchedule(task.copy(startAt = startOfToday, scheduledStartAt = slot.startAt, scheduledEndAt = slot.endAt)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Setujui jadwal hari ini")
                        }
                    }
                }
            }
        }
        Text("Tugas yang belum masuk usulan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val notProposed = candidates.filter { candidate -> proposed.none { it.id == candidate.id } }
        if (notProposed.isEmpty()) Text("Semua kandidat yang memenuhi kapasitas sudah ditampilkan.")
        else notProposed.forEach { task ->
            ListItem(
                headlineContent = { Text(task.title) },
                supportingContent = { Text("${task.priority} · " + if (task.estimatedMinutes > 0) "${task.estimatedMinutes} menit" else "Belum ada estimasi durasi") },
                modifier = Modifier.clickable { onOpenTask(task) }
            )
        }
        Text("Usulan dibuat berurutan mulai pukul 08:00, menghindari blok waktu yang sudah terisi, dan dibatasi sampai pukul 20:00. Semua perubahan hanya disimpan setelah disetujui.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
