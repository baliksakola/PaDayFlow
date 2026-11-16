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
    val remainingCandidates = candidates.toMutableList()
    var proposedMinutes = 0
    val proposed = buildList {
        for (task in remainingCandidates) {
            val duration = task.estimatedMinutes.coerceAtLeast(0)
            if (duration > 0 && proposedMinutes + duration <= remainingMinutes) {
                add(task)
                proposedMinutes += duration
            }
        }
    }
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
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text("${task.priority} · ${task.estimatedMinutes} menit" +
                            (task.deadlineAt?.let { " · Deadline ${Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { onApproveSchedule(task) }, modifier = Modifier.fillMaxWidth()) {
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
        Text("Perhitungan memakai tanggal mulai dan estimasi durasi. Tugas tanpa estimasi tidak otomatis diusulkan. Jadwal yang disetujui ditempatkan pada awal hari ini; jam mulai rinci belum dikelola.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
