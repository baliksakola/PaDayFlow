package com.padayflow.app

import android.os.Bundle
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import com.padayflow.app.data.HabitDao
import com.padayflow.app.data.HabitEntity
import com.padayflow.app.data.HabitLogEntity
import com.padayflow.app.data.NoteDao
import com.padayflow.app.data.NoteEntity
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
        val database = PaDayFlowDatabase.getInstance(applicationContext)
        val settings = applicationContext.dataStore
        val themeFlow = settings.data.map { it[THEME_MODE] ?: "system" }
        val productiveHoursFlow = settings.data.map { it[DAILY_PRODUCTIVE_HOURS] ?: 6 }
        setContent {
            val themeMode by themeFlow.collectAsState(initial = "system")
            val productiveHours by productiveHoursFlow.collectAsState(initial = 6)
            PaDayFlowTheme(themeMode = themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    PaDayFlowApp(database, database.taskDao(), database.habitDao(), database.noteDao(),
                        themeMode = themeMode, onThemeChange = { selected ->
                            lifecycleScope.launch { settings.edit { it[THEME_MODE] = selected } }
                        }, productiveHours = productiveHours, onProductiveHoursChange = { hours ->
                            lifecycleScope.launch { settings.edit { it[DAILY_PRODUCTIVE_HOURS] = hours } }
                        })
                }
            }
        }
    }
}

@Composable
private fun PaDayFlowApp(database: PaDayFlowDatabase, dao: TaskDao, habitDao: HabitDao, noteDao: NoteDao, themeMode: String, onThemeChange: (String) -> Unit, productiveHours: Int, onProductiveHoursChange: (Int) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaskEntity?>(null) }
    var showHabitTracker by remember { mutableStateOf(false) }
    var showSmartAssistant by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBackupRestore by remember { mutableStateOf(false) }
    var openedNotesType by remember { mutableStateOf<String?>(null) }
    val tasks by dao.observeTasks().collectAsState(initial = emptyList())
    val habits by habitDao.observeHabits().collectAsState(initial = emptyList())
    val habitLogs by habitDao.observeLogs().collectAsState(initial = emptyList())
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
            3 -> CalendarScreen(tasks, Modifier.padding(padding), onEdit = { editing = it })
            4 -> when {
                openedNotesType != null -> NotesScreen(noteDao, openedNotesType!!, Modifier.padding(padding),
                    onBack = { openedNotesType = null })
                showHabitTracker -> HabitTrackerScreen(habitDao, Modifier.padding(padding), onBack = { showHabitTracker = false })
                showSmartAssistant -> SmartAssistantScreen(tasks, Modifier.padding(padding),
                    onBack = { showSmartAssistant = false }, onOpenTask = { editing = it })
                showAnalytics -> AnalyticsScreen(tasks, habits, habitLogs, Modifier.padding(padding),
                    onBack = { showAnalytics = false })
                showSettings -> SettingsScreen(themeMode, productiveHours, Modifier.padding(padding),
                    onBack = { showSettings = false }, onThemeChange = onThemeChange,
                    onProductiveHoursChange = onProductiveHoursChange)
                showBackupRestore -> BackupRestoreScreen(database, Modifier.padding(padding),
                    onBack = { showBackupRestore = false })
                else -> MoreMenu(Modifier.padding(padding),
                    onHabitClick = { showHabitTracker = true },
                    onNotesClick = { openedNotesType = "NOTE" },
                    onBrainDumpClick = { openedNotesType = "BRAIN_DUMP" },
                    onSmartAssistantClick = { showSmartAssistant = true },
                    onAnalyticsClick = { showAnalytics = true },
                    onSettingsClick = { showSettings = true },
                    onBackupClick = { showBackupRestore = true })
            }
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
private fun MoreMenu(modifier: Modifier = Modifier, onHabitClick: () -> Unit, onNotesClick: () -> Unit, onBrainDumpClick: () -> Unit, onSmartAssistantClick: () -> Unit, onAnalyticsClick: () -> Unit, onSettingsClick: () -> Unit, onBackupClick: () -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lainnya", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(onClick = onHabitClick, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Habit Tracker", fontWeight = FontWeight.SemiBold)
                    Text("Pantau kebiasaan, target mingguan, dan streak.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        Card(onClick = onNotesClick, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.StickyNote2, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Notes", fontWeight = FontWeight.SemiBold)
                    Text("Simpan catatan dan ide penting.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        Card(onClick = onSmartAssistantClick, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Smart Assistant", fontWeight = FontWeight.SemiBold)
                    Text("Analisis deadline, prioritas, dan risiko tugas secara offline.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        Card(onClick = onAnalyticsClick, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Analytics, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Analytics", fontWeight = FontWeight.SemiBold)
                    Text("Lihat ringkasan tugas, estimasi waktu, dan konsistensi habit.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        Card(onClick = onBrainDumpClick, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Brain Dump", fontWeight = FontWeight.SemiBold)
                    Text("Tuangkan ide dan pikiran tanpa struktur.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        Card(onClick = onBackupClick, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Backup & Restore", fontWeight = FontWeight.SemiBold)
                    Text("Simpan atau pulihkan data aplikasi melalui file JSON.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        Card(onClick = onSettingsClick, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Settings", fontWeight = FontWeight.SemiBold)
                    Text("Tema dan preferensi aplikasi.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        }
        Text("Modul lain akan ditambahkan bertahap.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(themeMode: String, productiveHours: Int, modifier: Modifier = Modifier,
                           onBack: () -> Unit, onThemeChange: (String) -> Unit,
                           onProductiveHoursChange: (Int) -> Unit) {
    var hoursInput by remember(productiveHours) { mutableStateOf(productiveHours.toString()) }
    val options = listOf("system" to "Ikuti sistem", "light" to "Terang", "dark" to "Gelap")
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tampilan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Pilih tema aplikasi. Perubahan disimpan di perangkat dan diterapkan langsung.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                options.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onThemeChange(value) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == value, onClick = { onThemeChange(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kapasitas produktif harian", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Batas waktu untuk pekerjaan dan tugas setiap hari. Nilai ini menjadi acuan rekomendasi Smart Assistant.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = hoursInput,
                    onValueChange = { hoursInput = it.filter(Char::isDigit).take(2) },
                    label = { Text("Jam produktif per hari") },
                    suffix = { Text("jam") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val parsedHours = hoursInput.toIntOrNull()
                Button(
                    enabled = parsedHours != null && parsedHours in 1..16 && parsedHours != productiveHours,
                    onClick = { parsedHours?.let(onProductiveHoursChange) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan kapasitas") }
                Text("Rentang yang diizinkan: 1–16 jam. Pengaturan ini belum otomatis menjadwalkan ulang tugas.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Penyimpanan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Data tugas, habit, dan catatan tersimpan secara lokal menggunakan Room. Gunakan menu Backup & Restore untuk membuat atau memulihkan salinan data.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tentang PaDayFlow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Versi 0.1.0 · Aplikasi produktivitas pribadi · Offline-first",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnalyticsScreen(
    tasks: List<TaskEntity>,
    habits: List<HabitEntity>,
    habitLogs: List<HabitLogEntity>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val today = LocalDate.now()
    val fromDate = today.minusDays(29)
    val zone = ZoneId.systemDefault()
    val fromMillis = fromDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val untilMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val periodTasks = tasks.filter { it.createdAt >= fromMillis && it.createdAt < untilMillis }
    val completed = periodTasks.count { it.status == "Selesai" }
    val open = periodTasks.count { it.status != "Selesai" && it.status != "Dibatalkan" }
    val cancelled = periodTasks.count { it.status == "Dibatalkan" }
    val completionRate = if (periodTasks.isEmpty()) 0 else (completed * 100 / periodTasks.size)
    val overdue = tasks.count { it.status != "Selesai" && it.status != "Dibatalkan" &&
        it.deadlineAt != null && it.deadlineAt < today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val categoryEstimates = periodTasks.groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.estimatedMinutes.coerceAtLeast(0) } }
        .filterValues { it > 0 }.toList().sortedByDescending { it.second }.take(5)
    val logsFrom = fromDate.toString()
    val logsUntil = today.toString()
    val habitCheckins = habitLogs.count { it.completed && it.date >= logsFrom && it.date <= logsUntil }
    val habitExpected = habits.sumOf { it.targetPerWeek.coerceIn(1, 7) } * 30.0 / 7.0
    val habitRate = if (habitExpected <= 0) 0 else (habitCheckins * 100 / habitExpected).toInt().coerceAtMost(100)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }
            Column(Modifier.weight(1f)) {
                Text("Analytics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Periode 30 hari: ${fromDate.format(dateFormatter)} – ${today.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Penyelesaian tugas", fontWeight = FontWeight.SemiBold)
                Text("${completionRate}%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("${completed} selesai dari ${periodTasks.size} tugas yang dibuat dalam periode ini.")
                LinearProgressIndicator(progress = { completionRate / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsMetric("Aktif", open.toString(), Modifier.weight(1f))
            AnalyticsMetric("Dibatalkan", cancelled.toString(), Modifier.weight(1f))
            AnalyticsMetric("Terlambat", overdue.toString(), Modifier.weight(1f))
        }
        Heading("Estimasi waktu per kategori", "Akumulasi estimasi durasi tugas yang dibuat dalam periode ini.")
        if (categoryEstimates.isEmpty()) {
            EmptyState("Belum ada data estimasi", "Isi estimasi durasi pada tugas untuk melihat distribusi waktu.")
        } else {
            categoryEstimates.forEach { (category, minutes) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category, fontWeight = FontWeight.Medium)
                        Text(if (minutes >= 60) "${minutes / 60} jam ${minutes % 60} menit" else "${minutes} menit",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LinearProgressIndicator(
                        progress = { minutes.toFloat() / categoryEstimates.maxOf { it.second }.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Heading("Konsistensi habit", "Check-in 30 hari terakhir dibandingkan target mingguan seluruh habit.")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${habitRate}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${habitCheckins} check-in · target periode sekitar ${habitExpected.toInt()} check-in")
                LinearProgressIndicator(progress = { habitRate / 100f }, modifier = Modifier.fillMaxWidth())
                if (habits.isEmpty()) Text("Tambahkan habit untuk mulai mengukur konsistensi.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Catatan: persentase tugas memakai jumlah tugas yang dibuat dalam 30 hari sebagai penyebut. Estimasi waktu bukan durasi aktual. Data habit membandingkan check-in dengan target mingguan.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AnalyticsMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmartAssistantScreen(
    tasks: List<TaskEntity>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenTask: (TaskEntity) -> Unit
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val startToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endSoon = today.plusDays(3).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val active = tasks.filter { it.status != "Selesai" && it.status != "Dibatalkan" }
    val overdue = active.filter { it.deadlineAt != null && it.deadlineAt < startToday }
    val dueSoon = active.filter { it.deadlineAt != null && it.deadlineAt >= startToday && it.deadlineAt < endSoon }
    val urgent = active.filter { it.priority == "Mendesak" || it.priority == "Tinggi" }
    val noSchedule = active.filter { it.startAt == null && it.deadlineAt == null }
    val suggestions = buildList {
        overdue.forEach { add("Terlambat: ${it.title}. Tinjau progres dan tentukan jadwal tindak lanjut.") }
        dueSoon.forEach { add("Deadline mendekat: ${it.title}. Sisihkan waktu sebelum ${formatDate(it.deadlineAt!!)}.") }
        urgent.filter { task -> overdue.none { it.id == task.id } && dueSoon.none { it.id == task.id } }
            .forEach { add("Prioritas tinggi: ${it.title}. Pertimbangkan untuk mengerjakannya lebih awal.") }
        noSchedule.take(5).forEach { add("Belum dijadwalkan: ${it.title}. Tetapkan tanggal mulai atau deadline agar mudah dipantau.") }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }
            Column(Modifier.weight(1f)) {
                Text("Smart Assistant", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Analisis lokal berdasarkan data tugas perangkat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Ringkasan kondisi tugas", fontWeight = FontWeight.SemiBold)
                Text("${active.size} tugas aktif", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${overdue.size} terlambat · ${dueSoon.size} deadline 3 hari ke depan · ${urgent.size} prioritas tinggi/mendesak")
            }
        }
        Heading("Rekomendasi", "Saran dibuat dari aturan sederhana, tanpa layanan AI berbayar.")
        if (suggestions.isEmpty()) {
            EmptyState("Belum ada risiko yang terdeteksi", "Tetap periksa daftar tugas dan pastikan tanggal serta prioritas sudah diperbarui.")
        } else {
            suggestions.forEachIndexed { index, suggestion ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${index + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(suggestion, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Heading("Tugas yang perlu diperiksa", "Pilih tugas untuk membuka detail dan mengubahnya secara manual.")
        (overdue + dueSoon + urgent.filter { task -> overdue.none { it.id == task.id } && dueSoon.none { it.id == task.id } })
            .distinctBy { it.id }.take(10).forEach { task -> TaskCard(task, onClick = { onOpenTask(task) }) }
        Text("Catatan: rekomendasi ini tidak mengubah tugas, jadwal, atau status secara otomatis.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NotesScreen(dao: NoteDao, type: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val notes by dao.observeByType(type).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val isBrainDump = type == "BRAIN_DUMP"
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }
            Column(Modifier.weight(1f)) {
                Text(if (isBrainDump) "Brain Dump" else "Notes", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text(if (isBrainDump) "Catat semua ide yang muncul." else "Kumpulan catatan pribadi.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, contentDescription = "Tambah catatan") }
        }
        if (notes.isEmpty()) {
            EmptyState(if (isBrainDump) "Belum ada ide" else "Belum ada catatan",
                "Tekan tombol + untuk mulai menulis.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { note ->
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(note.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            if (note.content.isNotBlank()) Text(note.content, maxLines = 4,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { dao.delete(note) } } }) {
                                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                                }
                                TextButton(onClick = { editing = note }) { Text("Edit") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) NoteEditorDialog(null, isBrainDump, onDismiss = { adding = false }, onSave = { title, content ->
        scope.launch { withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.insert(NoteEntity(title = title, content = content, type = type, createdAt = now, updatedAt = now))
        } }
        adding = false
    })
    editing?.let { note ->
        NoteEditorDialog(note, isBrainDump, onDismiss = { editing = null }, onSave = { title, content ->
            scope.launch { withContext(Dispatchers.IO) {
                dao.update(note.copy(title = title, content = content, updatedAt = System.currentTimeMillis()))
            } }
            editing = null
        })
    }
}

@Composable
private fun NoteEditorDialog(
    note: NoteEntity?,
    isBrainDump: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var content by remember(note?.id) { mutableStateOf(note?.content.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note == null) if (isBrainDump) "Tulis Brain Dump" else "Catatan baru" else "Edit catatan") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isBrainDump) "Judul / ide utama" else "Judul catatan") }, singleLine = true)
                OutlinedTextField(content, { content = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isBrainDump) "Tuangkan semua pikiran di sini..." else "Isi catatan") },
                    minLines = 5)
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), content.trim()) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun HabitTrackerScreen(
    dao: HabitDao,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val habits by dao.observeHabits().collectAsState(initial = emptyList())
    val logs by dao.observeLogs().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var habitName by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("7") }
    val today = LocalDate.now()
    val todayKey = today.toString()
    val weekStart = today.with(java.time.DayOfWeek.MONDAY)

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }
            Column(Modifier.weight(1f)) {
                Text("Habit Tracker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Bangun konsistensi setiap hari.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, contentDescription = "Tambah kebiasaan") }
        }
        if (habits.isEmpty()) {
            EmptyState("Belum ada kebiasaan", "Tambahkan kebiasaan pertama, misalnya membaca, olahraga, atau minum air.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(habits, key = { it.id }) { habit ->
                    val completedDates = logs.filter { it.habitId == habit.id && it.completed }.map { it.date }.toSet()
                    val doneToday = todayKey in completedDates
                    val weeklyCount = (0..6).count { weekStart.plusDays(it.toLong()).toString() in completedDates }
                    val streak = calculateHabitStreak(completedDates, today)
                    var menuExpanded by remember(habit.id) { mutableStateOf(false) }
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(habit.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                    Text("$weeklyCount / ${habit.targetPerWeek} kali minggu ini",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Streak: $streak hari berturut-turut",
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Menu kebiasaan") }
                                    DropdownMenu(menuExpanded, { menuExpanded = false }) {
                                        DropdownMenuItem(text = { Text("Hapus kebiasaan") }, onClick = {
                                            menuExpanded = false
                                            scope.launch { withContext(Dispatchers.IO) {
                                                dao.deleteLogsForHabit(habit.id)
                                                dao.deleteHabit(habit.id)
                                            } }
                                        })
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(7) { index ->
                                    val dateKey = weekStart.plusDays(index.toLong()).toString()
                                    val checked = dateKey in completedDates
                                    Surface(
                                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp).height(30.dp)
                                            .clickable {
                                                scope.launch { withContext(Dispatchers.IO) {
                                                    if (checked) dao.removeLog(habit.id, dateKey)
                                                    else dao.saveLog(HabitLogEntity(habit.id, dateKey))
                                                } }
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(listOf("S", "S", "R", "K", "J", "S", "M")[index],
                                                color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (doneToday) "Selesai hari ini" else "Belum selesai hari ini",
                                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FilledTonalButton(onClick = {
                                    scope.launch { withContext(Dispatchers.IO) {
                                        if (doneToday) dao.removeLog(habit.id, todayKey)
                                        else dao.saveLog(HabitLogEntity(habit.id, todayKey))
                                    } }
                                }) { Text(if (doneToday) "Batalkan" else "Tandai selesai") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Tambah kebiasaan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(habitName, { habitName = it }, label = { Text("Nama kebiasaan") }, singleLine = true)
                    OutlinedTextField(target, { target = it.filter(Char::isDigit).take(1) },
                        label = { Text("Target per minggu (1–7)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = habitName.isNotBlank() && (target.toIntOrNull() ?: 0) in 1..7, onClick = {
                    scope.launch { withContext(Dispatchers.IO) {
                        dao.insertHabit(HabitEntity(name = habitName.trim(), targetPerWeek = target.toInt()))
                    } }
                    habitName = ""; target = "7"; adding = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun CalendarScreen(
    tasks: List<TaskEntity>,
    modifier: Modifier = Modifier,
    onEdit: (TaskEntity) -> Unit
) {
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val monthTitle = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID")))
        .replaceFirstChar { it.uppercase(Locale("id", "ID")) }
    val daysInMonth = month.lengthOfMonth()
    val firstOffset = month.dayOfWeek.value - 1
    val today = LocalDate.now()

    fun hasTask(date: LocalDate): Boolean = tasks.any { task ->
        task.startAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == date } == true ||
            task.deadlineAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == date } == true
    }

    val selectedTasks = tasks.filter { task ->
        task.startAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate } == true ||
            task.deadlineAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() == selectedDate } == true
    }.distinctBy { it.id }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Kalender", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1); selectedDate = month }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Bulan sebelumnya")
                    }
                    Text(monthTitle, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    IconButton(onClick = { month = month.plusMonths(1); selectedDate = month }) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "Bulan berikutnya")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val cellCount = ((firstOffset + daysInMonth + 6) / 7) * 7
                for (weekStart in 0 until cellCount step 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (column in 0..6) {
                            val dayNumber = weekStart + column - firstOffset + 1
                            if (dayNumber in 1..daysInMonth) {
                                val date = month.withDayOfMonth(dayNumber)
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                Column(Modifier.weight(1f).height(48.dp).clickable { selectedDate = date },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else if (isToday) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surface
                                    ) {
                                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                            Text(dayNumber.toString(), color = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                    if (hasTask(date)) {
                                        Spacer(Modifier.height(2.dp))
                                        Surface(Modifier.size(4.dp), shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primary) {}
                                    } else Spacer(Modifier.height(6.dp))
                                }
                            } else {
                                Spacer(Modifier.weight(1f).height(48.dp))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(7.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {}
                    Text("Ada tugas mulai atau deadline", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))
                    .replaceFirstChar { it.uppercase(Locale("id", "ID")) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(selectedTasks.size.toString() + " agenda", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { month = today.withDayOfMonth(1); selectedDate = today }) { Text("Hari ini") }
        }
        if (selectedTasks.isEmpty()) {
            EmptyState("Tidak ada agenda", "Tidak ada tanggal mulai atau deadline tugas pada tanggal ini.")
        } else {
            selectedTasks.forEach { task ->
                TaskCard(task, onClick = { onEdit(task) })
            }
        }
        Spacer(Modifier.height(72.dp))
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

private fun calculateHabitStreak(completedDates: Set<String>, today: LocalDate): Int {
    var date = if (today.toString() in completedDates) today else today.minusDays(1)
    var streak = 0
    while (date.toString() in completedDates) {
        streak++
        date = date.minusDays(1)
    }
    return streak
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
