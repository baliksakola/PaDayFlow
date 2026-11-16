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

@Composable
internal fun DailyPlanningScreen(
    tasks: List<TaskEntity>,
    productiveHours: Int,
    protectedTimes: String,
    workdayStart: String,
    workdayEnd: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onApproveSchedule: (TaskEntity) -> Unit
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val startTime = runCatching { LocalTime.parse(workdayStart) }.getOrDefault(LocalTime.of(8, 0))
    val endTime = runCatching { LocalTime.parse(workdayEnd) }.getOrDefault(LocalTime.of(20, 0))
    val plan = TimeBlockPlanner.plan(today, tasks, productiveHours, protectedTimes, zone, startTime, endTime)
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
        if (plannedMinutes > capacityMinutes) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Beban kerja melebihi kapasitas", fontWeight = FontWeight.SemiBold)
                    Text("Estimasi tugas terjadwal ${plannedMinutes / 60} jam ${plannedMinutes % 60} menit, sedangkan kapasitas harian hanya ${productiveHours} jam. Pertimbangkan memindahkan sebagian tugas.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (plannedMinutes >= capacityMinutes * 0.8f) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text("Kapasitas hampir penuh. Sisakan ruang untuk pekerjaan tak terduga dan jeda.", Modifier.padding(14.dp),
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
        Text("Timeline terpadu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Semua aktivitas hari ini diurutkan berdasarkan waktu. Usulan belum menjadi jadwal sampai disetujui.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        data class TimelineRow(
            val startAt: Long,
            val endAt: Long,
            val title: String,
            val detail: String,
            val kind: Int,
            val task: TaskEntity? = null
        )
        val timelineRows = buildList {
            todayTasks.filter { it.scheduledStartAt != null && it.scheduledEndAt != null }.forEach { task ->
                add(TimelineRow(task.scheduledStartAt!!, task.scheduledEndAt!!, task.title,
                    "${task.category} · ${task.status}", 0, task))
            }
            plan.protectedSlots.forEach { block ->
                add(TimelineRow(block.startAt, block.endAt, block.label, "Waktu pribadi yang dilindungi", 1))
            }
            plan.freeSlots.filter { it.endAt - it.startAt >= 15 * 60_000L }.forEach { gap ->
                val minutes = ((gap.endAt - gap.startAt) / 60_000L).toInt()
                add(TimelineRow(gap.startAt, gap.endAt, "Waktu kosong",
                    "${minutes / 60} jam ${minutes % 60} menit tersedia", 2))
            }
            proposedSlots.forEach { slot ->
                add(TimelineRow(slot.startAt, slot.endAt, slot.task.title,
                    "Usulan · ${slot.rationale} · ${slot.deadlineNote}", 3, slot.task))
            }
        }.sortedWith(compareBy<TimelineRow> { it.startAt }.thenBy { it.kind }.thenBy { it.endAt })
        if (timelineRows.isEmpty()) {
            Text("Belum ada jadwal, Protected Time, atau usulan untuk ditampilkan.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            timelineRows.forEach { row ->
                val startLabel = Instant.ofEpochMilli(row.startAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                val endLabel = Instant.ofEpochMilli(row.endAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                val containerColor = when (row.kind) {
                    1 -> MaterialTheme.colorScheme.tertiaryContainer
                    2 -> MaterialTheme.colorScheme.secondaryContainer
                    3 -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
                Card(
                    Modifier.fillMaxWidth().then(if (row.task != null) Modifier.clickable { onOpenTask(row.task) } else Modifier),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(startLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(endLabel, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.title, fontWeight = FontWeight.SemiBold)
                            Text(row.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (row.kind == 3) {
                                Text("Belum disimpan · perlu persetujuan", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        Text("Usulan penjadwalan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Tugas tanpa jadwal dan tugas tertunda yang belum selesai diurutkan berdasarkan prioritas dan deadline. Jadwal lama hanya diganti setelah disetujui.",
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
                        val isOverdue = (task.startAt != null && task.startAt < startOfToday) ||
                            (task.deadlineAt != null && task.deadlineAt < startOfToday)
                        if (isOverdue) {
                            AssistChip(onClick = { onOpenTask(task) }, label = { Text("Tertunda · perlu dijadwalkan ulang") })
                        }
                        Text("${Instant.ofEpochMilli(slot.startAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))}–${Instant.ofEpochMilli(slot.endAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${task.estimatedMinutes} menit")
                        Text("${slot.rationale} · ${slot.deadlineNote}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text("${task.deadlineAt?.let { "Tanggal deadline: ${Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}" } ?: "Tidak memiliki tanggal deadline"}",
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
        Text("Usulan dibuat antara pukul ${workdayStart}–${workdayEnd}, menghindari jadwal tugas dan Protected Time. Semua perubahan hanya disimpan setelah disetujui.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

