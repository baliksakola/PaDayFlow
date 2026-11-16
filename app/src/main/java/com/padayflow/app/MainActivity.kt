package com.padayflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.padayflow.app.data.PaDayFlowDatabase
import com.padayflow.app.data.TaskDao
import com.padayflow.app.data.TaskEntity
import com.padayflow.app.ui.theme.PaDayFlowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tabs = listOf("Beranda", "My Day", "Tugas", "Kalender", "Lainnya")
private val tabIcons = listOf(Icons.Outlined.Home, Icons.Outlined.Today, Icons.Outlined.TaskAlt, Icons.Outlined.CalendarMonth, Icons.Outlined.MoreHoriz)
private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = PaDayFlowDatabase.getInstance(applicationContext).taskDao()
        setContent { PaDayFlowTheme { Surface(Modifier.fillMaxSize()) { PaDayFlowApp(dao) } } }
    }
}

@Composable
private fun PaDayFlowApp(dao: TaskDao) {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaskEntity?>(null) }
    val tasks by dao.observeTasks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Icon(tabIcons[index], contentDescription = label) }, label = { Text(label) })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Tambah tugas")
            }
        }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(tasks, Modifier.padding(padding), onOpenTask = { editing = it })
            1 -> MyDay(tasks, Modifier.padding(padding),
                onComplete = { task -> scope.launch { withContext(Dispatchers.IO) { dao.updateStatus(task.id, "Selesai") } } },
                onEdit = { editing = it }, onDelete = { deleting = it })
            2 -> TaskList(tasks, Modifier.padding(padding), title = "Semua tugas",
                onComplete = { task -> scope.launch { withContext(Dispatchers.IO) { dao.updateStatus(task.id, "Selesai") } } },
                onEdit = { editing = it }, onDelete = { deleting = it })
            else -> Placeholder(tabs[tab], Modifier.padding(padding))
        }
    }

    if (adding) TaskEditorDialog(task = null, onDismiss = { adding = false }, onSave = { task ->
        scope.launch {
            withContext(Dispatchers.IO) { dao.insert(task) }
            adding = false
            tab = 2
        }
    })
    editing?.let { task ->
        TaskEditorDialog(task = task, onDismiss = { editing = null }, onSave = { updated ->
            scope.launch {
                withContext(Dispatchers.IO) { dao.update(updated) }
                editing = null
            }
        })
    }
    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus tugas?") },
            text = { Text("Tugas \"" + task.title + "\" akan dihapus permanen dari perangkat.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { dao.delete(task.id) }; deleting = null }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun Dashboard(tasks: List<TaskEntity>, modifier: Modifier = Modifier, onOpenTask: (TaskEntity) -> Unit) {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))
    val done = tasks.count { it.status == "Selesai" }
    val open = tasks.filter { it.status != "Selesai" && it.status != "Dibatalkan" }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PaDayFlow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(date.replaceFirstChar { it.uppercase(Locale("id", "ID")) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Selamat datang.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Susun prioritas, lalu kerjakan satu per satu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ringkasan tugas", fontWeight = FontWeight.SemiBold)
                Text(done.toString() + " selesai · " + open.size.toString() + " belum selesai",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(tasks.size.toString() + " tugas tersimpan di perangkat")
            }
        }
        Heading("Agenda dan tugas terbaru", "Pilih tugas untuk melihat atau mengubah detail")
        if (tasks.isEmpty()) EmptyState("Belum ada agenda", "Tekan tombol + untuk menambahkan tugas pertama.")
        else tasks.take(4).forEach { TaskCard(it, onClick = { onOpenTask(it) }) }
        Heading("Prioritas utama", "Tugas tinggi dan mendesak")
        val priorities = open.filter { it.priority == "Tinggi" || it.priority == "Mendesak" }.take(3)
        if (priorities.isEmpty()) EmptyState("Belum ada prioritas tinggi", "Tandai tugas penting dengan prioritas Tinggi atau Mendesak.")
        else priorities.forEach { TaskCard(it, onClick = { onOpenTask(it) }) }
        Spacer(Modifier.height(70.dp))
    }
}

@Composable
private fun MyDay(
    tasks: List<TaskEntity>,
    modifier: Modifier = Modifier,
    onComplete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit
) {
    val today = LocalDate.now()
    val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val todayTasks = tasks.filter { it.startAt != null && it.startAt >= start && it.startAt < end }
    TaskList(todayTasks, modifier, title = "My Day", emptyTitle = "Belum ada jadwal hari ini",
        emptyDescription = "Tugas akan muncul di sini jika tanggal mulainya diatur ke hari ini.",
        onComplete = onComplete, onEdit = onEdit, onDelete = onDelete)
}

@Composable
private fun TaskList(
    tasks: List<TaskEntity>,
    modifier: Modifier = Modifier,
    title: String = "Semua tugas",
    emptyTitle: String = "Belum ada tugas",
    emptyDescription: String = "Tekan tombol + untuk membuat tugas.",
    onComplete: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit
) {
    var statusFilter by remember(title) { mutableStateOf("Semua") }
    var categoryFilter by remember(title) { mutableStateOf("Semua") }
    var priorityFilter by remember(title) { mutableStateOf("Semua") }
    var sortByDeadline by remember(title) { mutableStateOf(false) }
    var filterMenu by remember(title) { mutableStateOf("") }

    val categories = listOf("Semua") + tasks.map { it.category }.distinct().sorted()
    val statuses = listOf("Semua", "Belum dikerjakan", "Sedang dikerjakan", "Tertunda", "Menunggu orang lain", "Selesai", "Dibatalkan")
    val priorities = listOf("Semua", "Mendesak", "Tinggi", "Normal", "Rendah")
    val filtered = tasks.filter {
        (statusFilter == "Semua" || it.status == statusFilter) &&
        (categoryFilter == "Semua" || it.category == categoryFilter) &&
        (priorityFilter == "Semua" || it.priority == priorityFilter)
    }.let { list ->
        if (sortByDeadline) list.sortedWith(compareBy<TaskEntity> { it.deadlineAt == null }.thenBy { it.deadlineAt })
        else list
    }

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(filtered.size.toString() + " dari " + tasks.size + " tugas", modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                FilterChip(selected = statusFilter != "Semua", onClick = { filterMenu = "status" },
                    label = { Text("Status") })
                DropdownMenu(filterMenu == "status", { filterMenu = "" }) {
                    statuses.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { statusFilter = value; filterMenu = "" }) }
                }
            }
            Box {
                FilterChip(selected = categoryFilter != "Semua", onClick = { filterMenu = "category" },
                    label = { Text("Kategori") })
                DropdownMenu(filterMenu == "category", { filterMenu = "" }) {
                    categories.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { categoryFilter = value; filterMenu = "" }) }
                }
            }
            Box {
                FilterChip(selected = priorityFilter != "Semua", onClick = { filterMenu = "priority" },
                    label = { Text("Prioritas") })
                DropdownMenu(filterMenu == "priority", { filterMenu = "" }) {
                    priorities.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { priorityFilter = value; filterMenu = "" }) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { sortByDeadline = !sortByDeadline }) {
                Icon(Icons.Outlined.Sort, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (sortByDeadline) "Deadline terdekat" else "Urutkan: terbaru")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                statusFilter = "Semua"; categoryFilter = "Semua"; priorityFilter = "Semua"; sortByDeadline = false
            }) { Text("Reset") }
        }
        if (filtered.isEmpty()) EmptyState(if (tasks.isEmpty()) emptyTitle else "Tidak ada hasil",
            if (tasks.isEmpty()) emptyDescription else "Ubah atau reset filter untuk melihat tugas lain.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { task ->
                TaskCard(task, onClick = { onEdit(task) }, onComplete = { onComplete(task) }, onDelete = { onDelete(task) })
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onClick: () -> Unit,
    onComplete: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(task.title, fontWeight = FontWeight.SemiBold)
            Text(task.category + " · " + task.priority, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(task.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            task.deadlineAt?.let {
                Text("Deadline: " + formatDate(it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onComplete != null || onDelete != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (task.status != "Selesai" && onComplete != null) TextButton(onClick = onComplete) { Text("Selesai") }
                    if (onDelete != null) TextButton(onClick = onDelete) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onClick) { Text("Edit") }
                }
            }
        }
    }
}

@Composable
private fun TaskEditorDialog(task: TaskEntity?, onDismiss: () -> Unit, onSave: (TaskEntity) -> Unit) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var description by remember(task?.id) { mutableStateOf(task?.description.orEmpty()) }
    var category by remember(task?.id) { mutableStateOf(task?.category ?: "Pribadi") }
    var priority by remember(task?.id) { mutableStateOf(task?.priority ?: "Normal") }
    var status by remember(task?.id) { mutableStateOf(task?.status ?: "Belum dikerjakan") }
    var startDate by remember(task?.id) { mutableStateOf(task?.startAt?.let(::formatDate).orEmpty()) }
    var deadline by remember(task?.id) { mutableStateOf(task?.deadlineAt?.let(::formatDate).orEmpty()) }
    var estimate by remember(task?.id) { mutableStateOf(if (task == null || task.estimatedMinutes == 0) "" else task.estimatedMinutes.toString()) }
    var reminder by remember(task?.id) { mutableStateOf(task?.reminderAt?.let(::formatDate).orEmpty()) }
    var notes by remember(task?.id) { mutableStateOf(task?.notes.orEmpty()) }
    var categoryMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Tambah tugas" else "Edit tugas") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nama tugas *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Deskripsi") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Box {
                    TextButton(onClick = { categoryMenu = true }) { Text("Kategori: " + category + " ▾") }
                    DropdownMenu(categoryMenu, { categoryMenu = false }) {
                        listOf("Sekolah – Sarpras", "Sekolah – Administrasi", "Pekerjaan Sampingan", "Pribadi", "Keluarga").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { category = it; categoryMenu = false })
                        }
                    }
                }
                Box {
                    TextButton(onClick = { priorityMenu = true }) { Text("Prioritas: " + priority + " ▾") }
                    DropdownMenu(priorityMenu, { priorityMenu = false }) {
                        listOf("Rendah", "Normal", "Tinggi", "Mendesak").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { priority = it; priorityMenu = false })
                        }
                    }
                }
                Box {
                    TextButton(onClick = { statusMenu = true }) { Text("Status: " + status + " ▾") }
                    DropdownMenu(statusMenu, { statusMenu = false }) {
                        listOf("Belum dikerjakan", "Sedang dikerjakan", "Tertunda", "Menunggu orang lain", "Selesai", "Dibatalkan").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { status = it; statusMenu = false })
                        }
                    }
                }
                OutlinedTextField(startDate, { startDate = it }, label = { Text("Tanggal mulai (YYYY-MM-DD)") },
                    supportingText = { Text("Opsional. Contoh: 2026-11-20") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(deadline, { deadline = it }, label = { Text("Deadline (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(estimate, { estimate = it.filter(Char::isDigit) }, label = { Text("Estimasi durasi (menit)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reminder, { reminder = it }, label = { Text("Tanggal pengingat (YYYY-MM-DD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val start = parseDate(startDate)
                val due = parseDate(deadline)
                val remind = parseDate(reminder)
                val invalid = listOf(startDate to start, deadline to due, reminder to remind)
                    .firstOrNull { it.first.isNotBlank() && it.second == null }
                when {
                    title.isBlank() -> validation = "Nama tugas wajib diisi."
                    invalid != null -> validation = "Format tanggal harus YYYY-MM-DD."
                    start != null && due != null && due < start -> validation = "Deadline tidak boleh sebelum tanggal mulai."
                    else -> {
                        validation = null
                        onSave(TaskEntity(
                            id = task?.id ?: 0,
                            title = title.trim(),
                            category = category,
                            description = description.trim(),
                            priority = priority,
                            status = status,
                            startAt = start,
                            deadlineAt = due,
                            estimatedMinutes = estimate.toIntOrNull() ?: 0,
                            reminderAt = remind,
                            notes = notes.trim(),
                            createdAt = task?.createdAt ?: System.currentTimeMillis()
                        ))
                    }
                }
            }, enabled = title.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

private fun parseDate(value: String): Long? {
    if (value.isBlank()) return null
    return try {
        LocalDate.parse(value, dateFormatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) { null }
}

private fun formatDate(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Placeholder(title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title + " — segera dikembangkan", style = MaterialTheme.typography.titleLarge)
    }
}
