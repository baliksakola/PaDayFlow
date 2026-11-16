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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DailyPlanningScreen(
    tasks: List<TaskEntity>,
    productiveHours: Int,
    protectedTimes: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onApproveSchedule: (TaskEntity) -> Unit
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val plan = TimeBlockPlanner.plan(today, tasks, productiveHours, protectedTimes, zone)
    val todayTasks = plan.todayTasks
    val plannedMinutes = plan.plannedMinutes
    val capacityMinutes = plan.capacityMinutes
    val remainingMinutes = plan.remainingMinutes
    val candidates = plan.candidates
    val proposedSlots = plan.proposedSlots
    val proposed = proposedSlots.map { it.task }
    val conflicts = plan.conflicts
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
        if (conflicts.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Konflik antarjadwal terdeteksi", fontWeight = FontWeight.SemiBold)
                    conflicts.forEach { conflict ->
                        Text("${conflict.first.title} bertabrakan dengan ${conflict.second.title}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Buka salah satu tugas untuk mengubah jamnya.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (plan.protectedTimeConflicts.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Jadwal melewati Protected Time", fontWeight = FontWeight.SemiBold)
                    plan.protectedTimeConflicts.forEach { task ->
                        Text(task.title, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Waktu terlindungi tidak diubah otomatis. Atur ulang tugas secara manual.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Waktu terlindungi: " + protectedTimes.lines().count { it.isNotBlank() } + " blok", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                        Text("${task.deadlineAt?.let { "Deadline ${Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}" } ?: "Tanpa deadline"}",
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
        Text("Usulan dibuat mulai pukul 08:00, menghindari jadwal tugas dan Protected Time, serta dibatasi sampai pukul 20:00. Semua perubahan hanya disimpan setelah disetujui.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

