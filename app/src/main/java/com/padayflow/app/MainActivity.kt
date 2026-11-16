package com.padayflow.app

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tabs = listOf("Beranda", "My Day", "Tugas", "Kalender", "Lainnya")
private val tabIcons = listOf(Icons.Outlined.Home, Icons.Outlined.Today, Icons.Outlined.TaskAlt, Icons.Outlined.CalendarMonth, Icons.Outlined.MoreHoriz)
private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7101)
        }
        val database = PaDayFlowDatabase.getInstance(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            val pendingReminders = database.taskDao().getTasksWithPendingReminders(System.currentTimeMillis())
            ReminderScheduler.reconcile(applicationContext, pendingReminders)
        }
        val settings = applicationContext.dataStore
        val themeFlow = settings.data.map { it[THEME_MODE] ?: "system" }
        val productiveHoursFlow = settings.data.map { it[DAILY_PRODUCTIVE_HOURS] ?: 6 }
        val protectedTimesFlow = settings.data.map { it[PROTECTED_TIME_BLOCKS] ?: DEFAULT_PROTECTED_TIME_BLOCKS }
        val snoozeMinutesFlow = settings.data.map { it[REMINDER_SNOOZE_MINUTES] ?: 60 }
        val workdayStartFlow = settings.data.map { it[WORKDAY_START] ?: DEFAULT_WORKDAY_START }
        val workdayEndFlow = settings.data.map { it[WORKDAY_END] ?: DEFAULT_WORKDAY_END }
        setContent {
            val themeMode by themeFlow.collectAsState(initial = "system")
            val productiveHours by productiveHoursFlow.collectAsState(initial = 6)
            val protectedTimes by protectedTimesFlow.collectAsState(initial = DEFAULT_PROTECTED_TIME_BLOCKS)
            val snoozeMinutes by snoozeMinutesFlow.collectAsState(initial = 60)
            val workdayStart by workdayStartFlow.collectAsState(initial = DEFAULT_WORKDAY_START)
            val workdayEnd by workdayEndFlow.collectAsState(initial = DEFAULT_WORKDAY_END)
            PaDayFlowTheme(themeMode = themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    PaDayFlowApp(database, database.taskDao(), database.habitDao(), database.noteDao(),
                        initialTaskId = intent.getLongExtra("openTaskId", -1L),
                        themeMode = themeMode, onThemeChange = { selected ->
                            lifecycleScope.launch { settings.edit { it[THEME_MODE] = selected } }
                        }, productiveHours = productiveHours, onProductiveHoursChange = { hours ->
                            lifecycleScope.launch { settings.edit { it[DAILY_PRODUCTIVE_HOURS] = hours } }
                        }, protectedTimes = protectedTimes, onProtectedTimesChange = { value ->
                            lifecycleScope.launch { settings.edit { it[PROTECTED_TIME_BLOCKS] = value } }
                        }, snoozeMinutes = snoozeMinutes, onSnoozeMinutesChange = { value ->
                            lifecycleScope.launch { settings.edit { it[REMINDER_SNOOZE_MINUTES] = value } }
                        }, workdayStart = workdayStart, workdayEnd = workdayEnd,
                        onWorkdayChange = { start, end ->
                            lifecycleScope.launch { settings.edit { it[WORKDAY_START] = start; it[WORKDAY_END] = end } }
                        })
                }
            }
        }
    }
}

@Composable
private fun PaDayFlowApp(database: PaDayFlowDatabase, dao: TaskDao, habitDao: HabitDao, noteDao: NoteDao, initialTaskId: Long, themeMode: String, onThemeChange: (String) -> Unit, productiveHours: Int, onProductiveHoursChange: (Int) -> Unit, protectedTimes: String, onProtectedTimesChange: (String) -> Unit, snoozeMinutes: Int, onSnoozeMinutesChange: (Int) -> Unit, workdayStart: String, workdayEnd: String, onWorkdayChange: (String, String) -> Unit) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TaskEntity?>(null) }
    var showHabitTracker by remember { mutableStateOf(false) }
    var showSmartAssistant by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBackupRestore by remember { mutableStateOf(false) }
    var showDailyPlanning by remember { mutableStateOf(false) }
    var openedNotesType by remember { mutableStateOf<String?>(null) }
    val tasks by dao.observeTasks().collectAsState(initial = emptyList())
    val habits by habitDao.observeHabits().collectAsState(initial = emptyList())
    val habitLogs by habitDao.observeLogs().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var handledNotificationTaskId by remember { mutableLongStateOf(-1L) }
    var lastBackPressedAt by remember { mutableLongStateOf(0L) }

    BackHandler {
        when {
            editing != null -> editing = null
            adding -> adding = false
            deleting != null -> deleting = null
            openedNotesType != null -> openedNotesType = null
            showHabitTracker -> showHabitTracker = false
            showSmartAssistant -> showSmartAssistant = false
            showAnalytics -> showAnalytics = false
            showSettings -> showSettings = false
            showBackupRestore -> showBackupRestore = false
            showDailyPlanning -> showDailyPlanning = false
            tab != 0 -> tab = 0
            else -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressedAt <= 2_000L) {
                    (activityContext as? android.app.Activity)?.finish()
                } else {
                    lastBackPressedAt = now
                    Toast.makeText(context, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    LaunchedEffect(initialTaskId, tasks) {
        if (initialTaskId > 0L && handledNotificationTaskId != initialTaskId) {
            tasks.firstOrNull { it.id == initialTaskId }?.let {
                editing = it
                tab = 2
                handledNotificationTaskId = initialTaskId
            }
        }
    }

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
                onComplete = { task -> scope.launch { withContext(Dispatchers.IO) { completeTaskAndScheduleNext(task, database, context) } } },
                onEdit = { editing = it }, onDelete = { deleting = it })
            2 -> TaskList(tasks, Modifier.padding(padding), title = "Semua tugas",
                onComplete = { task -> scope.launch { withContext(Dispatchers.IO) { completeTaskAndScheduleNext(task, database, context) } } },
                onEdit = { editing = it }, onDelete = { deleting = it })
            3 -> CalendarScreen(tasks, Modifier.padding(padding), onEdit = { editing = it })
            4 -> when {
                openedNotesType != null -> NotesScreen(noteDao, openedNotesType!!, Modifier.padding(padding),
                    onBack = { openedNotesType = null })
                showHabitTracker -> HabitTrackerScreen(habitDao, Modifier.padding(padding), onBack = { showHabitTracker = false })
                showSmartAssistant -> SmartAssistantScreen(tasks, productiveHours, Modifier.padding(padding),
                    onBack = { showSmartAssistant = false }, onOpenTask = { editing = it })
                showAnalytics -> AnalyticsScreen(tasks, habits, habitLogs, Modifier.padding(padding),
                    onBack = { showAnalytics = false })
                showSettings -> SettingsScreen(themeMode, productiveHours, protectedTimes, snoozeMinutes, workdayStart, workdayEnd, Modifier.padding(padding),
                    onBack = { showSettings = false }, onThemeChange = onThemeChange,
                    onProductiveHoursChange = onProductiveHoursChange, onProtectedTimesChange = onProtectedTimesChange,
                    onSnoozeMinutesChange = onSnoozeMinutesChange, onWorkdayChange = onWorkdayChange)
                showBackupRestore -> BackupRestoreScreen(database, Modifier.padding(padding),
                    onBack = { showBackupRestore = false })
                showDailyPlanning -> DailyPlanningScreen(tasks, productiveHours, protectedTimes, workdayStart, workdayEnd, Modifier.padding(padding),
                    onBack = { showDailyPlanning = false }, onOpenTask = { editing = it },
                    onApproveSchedule = { task ->
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.update(task) }
                        }
                    })
                else -> MoreMenu(Modifier.padding(padding),
                    onHabitClick = { showHabitTracker = true },
                    onNotesClick = { openedNotesType = "NOTE" },
                    onBrainDumpClick = { openedNotesType = "BRAIN_DUMP" },
                    onSmartAssistantClick = { showSmartAssistant = true },
                    onAnalyticsClick = { showAnalytics = true },
                    onSettingsClick = { showSettings = true },
                    onBackupClick = { showBackupRestore = true },
                    onDailyPlanningClick = { showDailyPlanning = true })
            }
            else -> Placeholder(tabs[tab], Modifier.padding(padding))
        }
    }

    if (adding) TaskEditorDialog(task = null, onDismiss = { adding = false }, onSave = { task ->
        scope.launch {
            withContext(Dispatchers.IO) {
                val newId = dao.insert(task)
                if (task.reminderAt != null && task.reminderAt > System.currentTimeMillis()) {
                    ReminderScheduler.schedule(context, newId, task.title, task.reminderAt)
                }
            }
            adding = false
            tab = 2
        }
    })
    editing?.let { task ->
        TaskEditorDialog(task = task, onDismiss = { editing = null }, onSave = { updated ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    dao.update(updated)
                    ReminderScheduler.cancel(context, updated.id)
                    if (updated.reminderAt != null && updated.reminderAt > System.currentTimeMillis()) {
                        ReminderScheduler.schedule(context, updated.id, updated.title, updated.reminderAt)
                    }
                }
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
                    scope.launch { withContext(Dispatchers.IO) { dao.delete(task.id) }; ReminderScheduler.cancel(context, task.id); deleting = null }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun Dashboard(tasks: List<TaskEntity>, modifier: Modifier = Modifier, onOpenTask: (TaskEntity) -> Unit) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val active = tasks.filter { it.status != "Selesai" && it.status != "Dibatalkan" }
    val done = tasks.count { it.status == "Selesai" }
    val todayTasks = active.filter { t -> (t.startAt != null && t.startAt >= start && t.startAt < end) || (t.scheduledStartAt != null && t.scheduledStartAt >= start && t.scheduledStartAt < end) }.distinctBy { it.id }.sortedBy { it.scheduledStartAt ?: it.startAt ?: Long.MAX_VALUE }
    val urgent = active.count { it.priority == "Mendesak" }
    val overdue = active.count { it.deadlineAt?.let { d -> d < start } == true }
    val dateDay = today.format(DateTimeFormatter.ofPattern("dd"))
    val dateMonth = today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))).uppercase(Locale("id", "ID"))
    val weekday = today.format(DateTimeFormatter.ofPattern("EEEE", Locale("id", "ID"))).uppercase(Locale("id", "ID"))
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("PA / DAYFLOW", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                Text("PERSONAL OPERATING SYSTEM", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("●  OFFLINE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(dateDay, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(weekday, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Text(dateMonth, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Hari ini bukan daftar tugas. Ini ruang untuk bergerak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PROGRES KESELURUHAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f), letterSpacing = 1.3.sp)
                    Text("$done / ${tasks.size}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                    Text("tugas diselesaikan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary)
                }
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { if (tasks.isEmpty()) 0f else done.toFloat() / tasks.size }, modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp, color = MaterialTheme.colorScheme.onPrimary, trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
                    Text("${if (tasks.isEmpty()) 0 else done * 100 / tasks.size}%", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            Column(Modifier.weight(1f)) { Text(todayTasks.size.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("AGENDA", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f)) { Text(urgent.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("MENDESAK", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f)) { Text(overdue.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("TERLAMBAT", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("AGENDA / 01", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Text("Ritme hari ini", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
            Text("${todayTasks.size} aktivitas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (todayTasks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Belum ada agenda.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Tambahkan tanggal mulai atau jadwal waktu pada tugas untuk membangun ritme harian.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        } else todayTasks.take(8).forEachIndexed { index, task ->
            Row(Modifier.fillMaxWidth().clickable { onOpenTask(task) }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(42.dp)) {
                    Text(task.scheduledStartAt?.let(::formatTime) ?: "— —", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (index != todayTasks.take(8).lastIndex) Surface(Modifier.padding(top = 8.dp).width(2.dp).height(42.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${task.category}  /  ${task.priority.uppercase(Locale("id", "ID"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                    if (task.deadlineAt != null) Text("DEADLINE  ${formatDate(task.deadlineAt)}", style = MaterialTheme.typography.labelSmall, color = if (task.deadlineAt < start) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ArrowOutward, contentDescription = "Buka tugas", tint = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        val focus = active.filter { it.priority == "Mendesak" || it.priority == "Tinggi" }.sortedWith(compareBy<TaskEntity>({ if (it.priority == "Mendesak") 0 else 1 }, { it.deadlineAt ?: Long.MAX_VALUE })).take(3)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text("FOCUS / 02", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Text("Jangan kehilangan arah", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        }
        if (focus.isEmpty()) Text("Tidak ada tugas prioritas tinggi saat ini.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        else focus.forEach { task -> Row(Modifier.fillMaxWidth().clickable { onOpenTask(task) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (task.priority == "Mendesak") "!" else "↑", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp)); Column(Modifier.weight(1f)) { Text(task.title, fontWeight = FontWeight.Bold); Text("${task.category} · ${task.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Outlined.ChevronRight, contentDescription = null) } }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    val todayTasks = tasks.filter { task ->
        task.status != "Selesai" && task.status != "Dibatalkan" &&
            ((task.startAt != null && task.startAt >= start && task.startAt < end) ||
             (task.scheduledStartAt != null && task.scheduledStartAt >= start && task.scheduledStartAt < end))
    }.distinctBy { it.id }.sortedWith(compareBy<TaskEntity>(
        { it.scheduledStartAt ?: it.startAt ?: Long.MAX_VALUE },
        { it.deadlineAt ?: Long.MAX_VALUE }
    ))
    TaskList(todayTasks, modifier, title = "My Day", emptyTitle = "Belum ada jadwal hari ini",
        emptyDescription = "Tugas dengan tanggal mulai atau jadwal waktu hari ini akan muncul di sini.",
        onComplete = onComplete, onEdit = onEdit, onDelete = onDelete)
}

@Composable
private fun TaskList(tasks: List<TaskEntity>, modifier: Modifier = Modifier, title: String = "Semua tugas", emptyTitle: String = "Belum ada tugas", emptyDescription: String = "Tekan tombol + untuk membuat tugas.", onComplete: (TaskEntity) -> Unit, onEdit: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit) {
    var statusFilter by remember(title) { mutableStateOf("Semua") }
    var categoryFilter by remember(title) { mutableStateOf("Semua") }
    var priorityFilter by remember(title) { mutableStateOf("Semua") }
    var sortByDeadline by remember(title) { mutableStateOf(false) }
    var filterMenu by remember(title) { mutableStateOf("") }
    val categories = listOf("Semua") + tasks.map { it.category }.distinct().sorted()
    val statuses = listOf("Semua", "Belum dikerjakan", "Sedang dikerjakan", "Tertunda", "Menunggu orang lain", "Selesai", "Dibatalkan")
    val priorities = listOf("Semua", "Mendesak", "Tinggi", "Normal", "Rendah")
    val filtered = tasks.filter { (statusFilter == "Semua" || it.status == statusFilter) && (categoryFilter == "Semua" || it.category == categoryFilter) && (priorityFilter == "Semua" || it.priority == priorityFilter) }.let { list -> if (sortByDeadline) list.sortedWith(compareBy<TaskEntity> { it.deadlineAt == null }.thenBy { it.deadlineAt }) else list }
    val activeCount = filtered.count { it.status != "Selesai" && it.status != "Dibatalkan" }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(if (title == "My Day") "TODAY / 02" else "LIBRARY / 04", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp); Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text(if (title == "My Day") "Ruang eksekusi untuk hari ini." else "Semua hal yang perlu kamu kelola.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(horizontalAlignment = Alignment.End) { Text(filtered.size.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Text("ITEM", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            Column(Modifier.weight(1f)) { Text(activeCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("AKTIF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }
            Column(Modifier.weight(1f)) { Text(filtered.count { it.status == "Selesai" }.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("SELESAI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }
            Column(Modifier.weight(1f)) { Text(filtered.count { it.priority == "Mendesak" }.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("MENDESAK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box { FilterChip(selected = statusFilter != "Semua", onClick = { filterMenu = "status" }, label = { Text("Status") }); DropdownMenu(filterMenu == "status", { filterMenu = "" }) { statuses.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { statusFilter = v; filterMenu = "" }) } } }
            Box { FilterChip(selected = categoryFilter != "Semua", onClick = { filterMenu = "category" }, label = { Text("Kategori") }); DropdownMenu(filterMenu == "category", { filterMenu = "" }) { categories.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { categoryFilter = v; filterMenu = "" }) } } }
            Box { FilterChip(selected = priorityFilter != "Semua", onClick = { filterMenu = "priority" }, label = { Text("Prioritas") }); DropdownMenu(filterMenu == "priority", { filterMenu = "" }) { priorities.forEach { v -> DropdownMenuItem(text = { Text(v) }, onClick = { priorityFilter = v; filterMenu = "" }) } } }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { sortByDeadline = !sortByDeadline }) { Icon(Icons.Outlined.Sort, contentDescription = null); Spacer(Modifier.width(5.dp)); Text(if (sortByDeadline) "Deadline terdekat" else "Urutan terbaru") }; Spacer(Modifier.weight(1f)); TextButton(onClick = { statusFilter = "Semua"; categoryFilter = "Semua"; priorityFilter = "Semua"; sortByDeadline = false }) { Text("Reset") } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (filtered.isEmpty()) EmptyState(if (tasks.isEmpty()) emptyTitle else "Tidak ada hasil", if (tasks.isEmpty()) emptyDescription else "Ubah atau reset filter untuk melihat tugas lain.")
        else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(filtered, key = { it.id }) { task ->
                Row(Modifier.fillMaxWidth().clickable { onEdit(task) }.padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = RoundedCornerShape(8.dp), color = when (task.priority) { "Mendesak" -> MaterialTheme.colorScheme.errorContainer; "Tinggi" -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }) { Text(when (task.priority) { "Mendesak" -> "!!"; "Tinggi" -> "↑"; else -> "·" }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (task.status == "Selesai") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text("${task.category.uppercase(Locale("id", "ID"))}  /  ${task.status.uppercase(Locale("id", "ID"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.3.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { task.deadlineAt?.let { Text("DUE ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }; task.scheduledStartAt?.let { Text(formatTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = { if (task.status != "Selesai" && task.status != "Dibatalkan") onComplete(task) }) { Icon(if (task.status == "Selesai") Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = "Tandai selesai", tint = if (task.status == "Selesai") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = { onDelete(task) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus tugas", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
@Composable
private fun CalendarScreen(tasks: List<TaskEntity>, modifier: Modifier = Modifier, onEdit: (TaskEntity) -> Unit) {
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var view by remember { mutableStateOf("Bulan") }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val days = month.lengthOfMonth()
    val offset = month.dayOfWeek.value - 1
    val monthTitle = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))).uppercase(Locale("id", "ID"))
    fun dates(task: TaskEntity) = buildSet { task.startAt?.let { add(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }; task.scheduledStartAt?.let { add(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }; task.deadlineAt?.let { add(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) } }
    fun hasItems(date: LocalDate) = tasks.any { date in dates(it) }
    val selected = tasks.filter { selectedDate in dates(it) }.distinctBy { it.id }.sortedWith(compareBy<TaskEntity>({ it.scheduledStartAt ?: it.startAt ?: it.deadlineAt ?: Long.MAX_VALUE }, { it.title.lowercase(Locale.getDefault()) }))
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val week = (0..6).map { weekStart.plusDays(it.toLong()) }
    val agenda = tasks.filter { task -> dates(task).any { !it.isBefore(today) && !it.isAfter(today.plusDays(30)) } }.distinctBy { it.id }.sortedBy { it.scheduledStartAt ?: it.startAt ?: it.deadlineAt ?: Long.MAX_VALUE }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("PLANNER / 03", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold); Text("Kalender", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Lihat waktu. Tentukan arah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(horizontalAlignment = Alignment.End) { Text(today.dayOfMonth.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Text(today.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale("id", "ID"))).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            listOf("Bulan", "Minggu", "Agenda").forEach { option ->
                val selectedMode = view == option
                Column(Modifier.weight(1f).clickable { view = option }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(option.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = if (selectedMode) FontWeight.Black else FontWeight.Normal, color = if (selectedMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
                    Surface(Modifier.fillMaxWidth().height(if (selectedMode) 3.dp else 1.dp), color = if (selectedMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) {}
                }
            }
        }
        if (view != "Agenda") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (view == "Bulan") { month = month.minusMonths(1); selectedDate = month } else selectedDate = selectedDate.minusWeeks(1) }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Periode sebelumnya") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(if (view == "Bulan") monthTitle else "${weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale("id", "ID")))} — ${weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("id", "ID")))}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp) }
                IconButton(onClick = { if (view == "Bulan") { month = month.plusMonths(1); selectedDate = month } else selectedDate = selectedDate.plusWeeks(1) }) { Icon(Icons.Outlined.ArrowForward, contentDescription = "Periode berikutnya") }
            }
            if (view == "Bulan") {
                Row(Modifier.fillMaxWidth()) { listOf("S", "S", "R", "K", "J", "S", "M").forEachIndexed { i, d -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(if (i == 0) "SEN" else when(i) { 1 -> "SEL"; 2 -> "RAB"; 3 -> "KAM"; 4 -> "JUM"; 5 -> "SAB"; else -> "MIN" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) } } }
                val count = ((offset + days + 6) / 7) * 7
                for (row in 0 until count step 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val number = row + col - offset + 1
                            if (number in 1..days) {
                                val date = month.withDayOfMonth(number); val chosen = date == selectedDate; val isToday = date == today
                                Column(Modifier.weight(1f).height(56.dp).clickable { selectedDate = date }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(shape = RoundedCornerShape(12.dp), color = if (chosen) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) { Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(number.toString(), fontWeight = if (chosen || isToday) FontWeight.Black else FontWeight.Normal, color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }
                                    if (hasItems(date)) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(minOf(tasks.count { date in dates(it) }, 3)) { Surface(Modifier.size(4.dp), shape = RoundedCornerShape(50), color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) {} } } else Spacer(Modifier.height(4.dp))
                                }
                            } else Spacer(Modifier.weight(1f).height(56.dp))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { week.forEach { date -> val chosen = date == selectedDate; Column(Modifier.weight(1f).clickable { selectedDate = date }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("id", "ID")).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Surface(shape = RoundedCornerShape(14.dp), color = if (chosen) MaterialTheme.colorScheme.primary else if (date == today) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) { Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { Text(date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }; if (hasItems(date)) Surface(Modifier.size(5.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiary) else Spacer(Modifier.height(5.dp)) } } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Surface(Modifier.size(7.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiary) {}; Text("Ada aktivitas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onClick = { month = today.withDayOfMonth(1); selectedDate = today }) { Text("Kembali ke hari ini") } }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("30 HARI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text("Agenda mendatang", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }; Text("${agenda.size} tugas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (agenda.isEmpty()) EmptyState("Belum ada agenda", "Tidak ada jadwal atau deadline dalam 30 hari ke depan.") else agenda.groupBy { task -> task.scheduledStartAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: task.startAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: task.deadlineAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today }.toSortedMap().forEach { (date, items) -> Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("id", "ID"))).uppercase(Locale("id", "ID")), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp); items.forEach { task -> Row(Modifier.fillMaxWidth().clickable { onEdit(task) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.width(52.dp)) { Text(task.scheduledStartAt?.let(::formatTime) ?: "ALL DAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }; Column(Modifier.weight(1f)) { Text(task.title, fontWeight = FontWeight.Bold); Text("${task.category} · ${task.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Outlined.ArrowOutward, contentDescription = null) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) } }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) { Column(Modifier.weight(1f)) { Text("SELECTED DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }; Text("${selected.size} item", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (selected.isEmpty()) Text("Tidak ada aktivitas pada tanggal ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else selected.forEach { task ->
            Row(Modifier.fillMaxWidth().clickable { onEdit(task) }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.width(54.dp)) { Text(task.scheduledStartAt?.let(::formatTime) ?: "— —", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); task.scheduledEndAt?.let { Text(formatTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(task.title, fontWeight = FontWeight.Bold); Text("${task.category} · ${task.priority} · ${task.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (task.deadlineAt != null) Text("DEADLINE ${formatDate(task.deadlineAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }
                Icon(Icons.Outlined.ArrowOutward, contentDescription = "Edit tugas", tint = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            task.scheduledStartAt?.let { start ->
                val end = task.scheduledEndAt
                val timeLabel = formatTime(start) + (end?.let { "–" + formatTime(it) } ?: "")
                Text("Jadwal: $timeLabel", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
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
    var scheduledStartTime by remember(task?.id) { mutableStateOf(task?.scheduledStartAt?.let(::formatTime).orEmpty()) }
    var scheduledEndTime by remember(task?.id) { mutableStateOf(task?.scheduledEndAt?.let(::formatTime).orEmpty()) }
    var recurrenceRule by remember(task?.id) { mutableStateOf(task?.recurrenceRule ?: "NONE") }
    var recurrenceInterval by remember(task?.id) { mutableStateOf((task?.recurrenceInterval ?: 1).toString()) }
    var recurrenceEnd by remember(task?.id) { mutableStateOf(task?.recurrenceEndAt?.let(::formatDate).orEmpty()) }
    var recurrenceMenu by remember { mutableStateOf(false) }
    var deadline by remember(task?.id) { mutableStateOf(task?.deadlineAt?.let(::formatDate).orEmpty()) }
    var estimate by remember(task?.id) { mutableStateOf(if (task == null || task.estimatedMinutes == 0) "" else task.estimatedMinutes.toString()) }
    var reminder by remember(task?.id) { mutableStateOf(task?.reminderAt?.let(::formatDate).orEmpty()) }
    var reminderTime by remember(task?.id) { mutableStateOf(task?.reminderAt?.let(::formatTime).orEmpty()) }
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
                CalendarDateField("Tanggal mulai", startDate, { startDate = it }, "Opsional")
                CalendarDateField("Deadline", deadline, { deadline = it })
                Text("Waktu pengerjaan (opsional)", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(scheduledStartTime, { scheduledStartTime = it }, label = { Text("Jam mulai (HH:mm)") },
                    supportingText = { Text("Gunakan tanggal mulai di atas. Contoh: 08:00") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(scheduledEndTime, { scheduledEndTime = it }, label = { Text("Jam selesai (HH:mm)") },
                    supportingText = { Text("Contoh: 09:30") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Pengulangan", style = MaterialTheme.typography.labelLarge)
                Box {
                    TextButton(onClick = { recurrenceMenu = true }) { Text("Ulangi: " + when (recurrenceRule) { "DAILY" -> "Setiap hari"; "WEEKLY" -> "Setiap minggu"; "MONTHLY" -> "Setiap bulan"; else -> "Tidak berulang" } + " ▾") }
                    DropdownMenu(recurrenceMenu, { recurrenceMenu = false }) {
                        listOf("NONE" to "Tidak berulang", "DAILY" to "Harian", "WEEKLY" to "Mingguan", "MONTHLY" to "Bulanan").forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { recurrenceRule = value; recurrenceMenu = false })
                        }
                    }
                }
                if (recurrenceRule != "NONE") {
                    OutlinedTextField(recurrenceInterval, { recurrenceInterval = it.filter(Char::isDigit).take(3) }, label = { Text("Interval pengulangan") }, supportingText = { Text("1 = setiap periode; 2 = setiap dua periode.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    CalendarDateField("Berakhir pada", recurrenceEnd, { recurrenceEnd = it }, "Opsional")
                }
                OutlinedTextField(estimate, { estimate = it.filter(Char::isDigit) }, label = { Text("Estimasi durasi (menit)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                CalendarDateField("Tanggal pengingat", reminder, { reminder = it }, "Opsional")
                OutlinedTextField(reminderTime, { reminderTime = it }, label = { Text("Jam pengingat (HH:mm)") },
                    supportingText = { Text("Opsional. Jika kosong, pengingat dijadwalkan pukul 09:00.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val start = parseDate(startDate)
                val due = parseDate(deadline)
                val remind = if (reminder.isBlank()) null else parseDateTime(reminder, reminderTime.ifBlank { "09:00" })
                val scheduledStart = parseDateTime(startDate, scheduledStartTime)
                val scheduledEnd = parseDateTime(startDate, scheduledEndTime)
                val repeatUntil = parseDate(recurrenceEnd)
                val repeatEvery = recurrenceInterval.toIntOrNull()
                val invalid = listOf(startDate to start, deadline to due, reminder to remind)
                    .firstOrNull { it.first.isNotBlank() && it.second == null }
                when {
                    title.isBlank() -> validation = "Nama tugas wajib diisi."
                    invalid != null -> validation = "Format tanggal harus YYYY-MM-DD."
                    reminder.isNotBlank() && remind == null -> validation = "Tanggal atau jam pengingat tidak valid. Gunakan YYYY-MM-DD dan HH:mm."
                    start != null && due != null && due < start -> validation = "Deadline tidak boleh sebelum tanggal mulai."
                    (scheduledStartTime.isNotBlank() || scheduledEndTime.isNotBlank()) && start == null -> validation = "Isi tanggal mulai sebelum mengatur jam pengerjaan."
                    (scheduledStartTime.isBlank() != scheduledEndTime.isBlank()) -> validation = "Jam mulai dan jam selesai harus diisi berpasangan."
                    scheduledStartTime.isNotBlank() && (scheduledStart == null || scheduledEnd == null) -> validation = "Format jam harus HH:mm, contoh 08:30."
                    scheduledStart != null && scheduledEnd != null && scheduledEnd!! <= scheduledStart!! -> validation = "Jam selesai harus setelah jam mulai."
                    recurrenceEnd.isNotBlank() && repeatUntil == null -> validation = "Tanggal akhir pengulangan harus YYYY-MM-DD."
                    recurrenceRule != "NONE" && (repeatEvery == null || repeatEvery !in 1..365) -> validation = "Interval pengulangan harus 1–365."
                    recurrenceRule != "NONE" && start == null -> validation = "Isi tanggal mulai untuk tugas berulang."
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
                            createdAt = task?.createdAt ?: System.currentTimeMillis(),
                            scheduledStartAt = scheduledStart,
                            scheduledEndAt = scheduledEnd,
                            recurrenceRule = recurrenceRule,
                            recurrenceInterval = if (recurrenceRule == "NONE") 1 else (repeatEvery ?: 1),
                            recurrenceEndAt = if (recurrenceRule == "NONE") null else repeatUntil
                        ))
                    }
                }
            }, enabled = title.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun CalendarDateField(label: String, value: String, onDateSelected: (String) -> Unit, supportingText: String? = null) {
    var showPicker by remember { mutableStateOf(false) }
    val initialMillis = remember(value) { runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
    OutlinedTextField(
        value = value.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))) }.getOrDefault(it) } ?: "",
        onValueChange = {}, readOnly = true, label = { Text(label) }, placeholder = { Text("Pilih tanggal") },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = { Row(verticalAlignment = Alignment.CenterVertically) { if (value.isNotBlank()) IconButton(onClick = { onDateSelected("") }) { Icon(Icons.Outlined.Close, contentDescription = "Hapus tanggal") }; IconButton(onClick = { showPicker = true }) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "Pilih tanggal") } } },
        singleLine = true, modifier = Modifier.fillMaxWidth().clickable { showPicker = true }
    )
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let { millis -> onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(dateFormatter)) }; showPicker = false }, enabled = pickerState.selectedDateMillis != null) { Text("Pilih") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Batal") } }) { DatePicker(state = pickerState, showModeToggle = true) }
    }
}

private suspend fun completeTaskAndScheduleNext(
    task: TaskEntity,
    database: PaDayFlowDatabase,
    context: android.content.Context
) {
    val dao = database.taskDao()
    val completion = database.withTransaction {
        val currentTask = dao.getTaskById(task.id)
        if (currentTask == null || dao.completeIfActive(task.id) != 1) {
            Triple(false, null, null)
        } else {
            val nextTask = RecurringTaskFactory.next(currentTask)
            val nextId = nextTask?.let { dao.insert(it) }
            Triple(true, nextTask, nextId)
        }
    }
    ReminderScheduler.cancel(context, task.id)
    val nextTask = completion.second
    val nextId = completion.third
    if (nextTask != null && nextId != null) {
        nextTask.reminderAt?.takeIf { it > System.currentTimeMillis() }?.let {
            ReminderScheduler.schedule(context, nextId, nextTask.title, it)
        }
    }
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

private fun parseDateTime(date: String, time: String): Long? {
    if (date.isBlank() || time.isBlank()) return null
    return try {
        LocalDate.parse(date, dateFormatter).atTime(LocalTime.parse(time))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) { null }
}

private fun formatTime(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))

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
