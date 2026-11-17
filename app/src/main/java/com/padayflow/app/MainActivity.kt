package com.padayflow.app

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

class MainActivity : FragmentActivity() {
    private fun showBiometricPrompt() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                appLockedState.value = false
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    Toast.makeText(this@MainActivity, errString, Toast.LENGTH_SHORT).show()
                }
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka PaDayFlow")
            .setSubtitle("Verifikasi menggunakan biometrik perangkat")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Gunakan PIN")
            .build()
        prompt.authenticate(promptInfo)
    }
    private var lockEnabledCached = false
    private val appLockedState = mutableStateOf(false)
\n    override fun onStop() {
        if (lockEnabledCached) appLockedState.value = true
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = PaDayFlowDatabase.getInstance(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            val allTasks = database.taskDao().getAllForBackup()
            ReminderScheduler.reconcile(applicationContext, allTasks)
        }
        val settings = applicationContext.dataStore
        val themeFlow = settings.data.map { it[THEME_MODE] ?: "system" }
        val pinHashFlow = settings.data.map { it[APP_PIN_HASH] ?: "" }
        val pinSaltFlow = settings.data.map { it[APP_PIN_SALT] ?: "" }
        val appLockEnabledFlow = settings.data.map { it[APP_LOCK_ENABLED] ?: false }
        val categoriesFlow = settings.data.map { (it[TASK_CATEGORIES] ?: DEFAULT_TASK_CATEGORIES).split("\n").map(String::trim).filter(String::isNotBlank) }
        val productiveHoursFlow = settings.data.map { it[DAILY_PRODUCTIVE_HOURS] ?: 6 }
        val protectedTimesFlow = settings.data.map { it[PROTECTED_TIME_BLOCKS] ?: DEFAULT_PROTECTED_TIME_BLOCKS }
        val snoozeMinutesFlow = settings.data.map { it[REMINDER_SNOOZE_MINUTES] ?: 60 }
        val workdayStartFlow = settings.data.map { it[WORKDAY_START] ?: DEFAULT_WORKDAY_START }
        val workdayEndFlow = settings.data.map { it[WORKDAY_END] ?: DEFAULT_WORKDAY_END }
        setContent {
            val themeMode by themeFlow.collectAsState(initial = "system")
            val pinHash by pinHashFlow.collectAsState(initial = "")
            val pinSalt by pinSaltFlow.collectAsState(initial = "")
            val appLockEnabled by appLockEnabledFlow.collectAsState(initial = false)
            val biometricAvailable = remember { BiometricManager.from(this@MainActivity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS }
            var securityInitialized by remember { mutableStateOf(false) }
            LaunchedEffect(appLockEnabled, pinHash) {
                lockEnabledCached = appLockEnabled && pinHash.isNotBlank()
                if (!securityInitialized) {
                    appLockedState.value = lockEnabledCached
                    securityInitialized = true
                }
            }
            val categories by categoriesFlow.collectAsState(initial = DEFAULT_TASK_CATEGORIES.split("\n"))
            val productiveHours by productiveHoursFlow.collectAsState(initial = 6)
            val protectedTimes by protectedTimesFlow.collectAsState(initial = DEFAULT_PROTECTED_TIME_BLOCKS)
            val snoozeMinutes by snoozeMinutesFlow.collectAsState(initial = 60)
            val workdayStart by workdayStartFlow.collectAsState(initial = DEFAULT_WORKDAY_START)
            val workdayEnd by workdayEndFlow.collectAsState(initial = DEFAULT_WORKDAY_END)
            PaDayFlowTheme(themeMode = themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    PaDayFlowApp(database, database.taskDao(), database.habitDao(), database.noteDao(), categories = categories, onCategoriesChange = { value -> lifecycleScope.launch { settings.edit { it[TASK_CATEGORIES] = value.joinToString("\n") } } },
                        pinHash = pinHash, pinSalt = pinSalt, appLockEnabled = appLockEnabled, appLocked = appLockedState.value, biometricAvailable = biometricAvailable, onBiometricUnlock = { showBiometricPrompt() }, onUnlock = { appLockedState.value = false },
                        onSetPin = { pin -> val salt = createPinSalt(); val hash = hashAppPin(pin, salt); lifecycleScope.launch { settings.edit { it[APP_PIN_SALT] = salt; it[APP_PIN_HASH] = hash; it[APP_LOCK_ENABLED] = true } } },
                        onRemovePin = { lifecycleScope.launch { settings.edit { it.remove(APP_PIN_HASH); it.remove(APP_PIN_SALT); it[APP_LOCK_ENABLED] = false } }; appLockedState.value = false },
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
private fun PaDayFlowApp(database: PaDayFlowDatabase, dao: TaskDao, habitDao: HabitDao, noteDao: NoteDao, categories: List<String>, onCategoriesChange: (List<String>) -> Unit, pinHash: String, pinSalt: String, appLockEnabled: Boolean, appLocked: Boolean, biometricAvailable: Boolean, onBiometricUnlock: () -> Unit, onUnlock: () -> Unit, onSetPin: (String) -> Unit, onRemovePin: () -> Unit, initialTaskId: Long, themeMode: String, onThemeChange: (String) -> Unit, productiveHours: Int, onProductiveHoursChange: (Int) -> Unit, protectedTimes: String, onProtectedTimesChange: (String) -> Unit, snoozeMinutes: Int, onSnoozeMinutesChange: (Int) -> Unit, workdayStart: String, workdayEnd: String, onWorkdayChange: (String, String) -> Unit) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    if (appLockEnabled && appLocked && pinHash.isNotBlank()) {
        AppLockScreen(expectedHash = pinHash, salt = pinSalt, biometricAvailable = biometricAvailable, onBiometricUnlock = onBiometricUnlock, onUnlock = onUnlock)
        return
    }
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
                showSettings -> SettingsScreen(themeMode, productiveHours, protectedTimes, snoozeMinutes, workdayStart, workdayEnd, categories, pinHash.isNotBlank(), onSetPin, onRemovePin, Modifier.padding(padding),
                    onBack = { showSettings = false }, onThemeChange = onThemeChange,
                    onProductiveHoursChange = onProductiveHoursChange, onProtectedTimesChange = onProtectedTimesChange,
                    onSnoozeMinutesChange = onSnoozeMinutesChange, onWorkdayChange = onWorkdayChange, onCategoriesChange = onCategoriesChange)
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

    if (adding) TaskEditorDialog(task = null, categories = categories, onDismiss = { adding = false }, onSave = { task ->
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
        TaskEditorDialog(task = task, categories = categories, onDismiss = { editing = null }, onSave = { updated ->
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
private fun MyDay(tasks: List<TaskEntity>, modifier: Modifier = Modifier, onComplete: (TaskEntity) -> Unit, onEdit: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val todayTasks = tasks.filter { task -> task.status != "Dibatalkan" && ((task.startAt != null && task.startAt >= start && task.startAt < end) || (task.scheduledStartAt != null && task.scheduledStartAt >= start && task.scheduledStartAt < end)) }.distinctBy { it.id }.sortedWith(compareBy<TaskEntity>({ it.scheduledStartAt ?: it.startAt ?: Long.MAX_VALUE }, { it.deadlineAt ?: Long.MAX_VALUE }))
    val done = todayTasks.count { it.status == "Selesai" }
    val active = todayTasks.size - done
    val percent = if (todayTasks.isEmpty()) 0 else done * 100 / todayTasks.size
    val dayNumber = today.format(DateTimeFormatter.ofPattern("dd"))
    val weekday = today.format(DateTimeFormatter.ofPattern("EEEE", Locale("id", "ID"))).uppercase(Locale("id", "ID"))
    val monthName = today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))).uppercase(Locale("id", "ID"))
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("EXECUTION / 02", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold); Text("My Day", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Pilih yang penting. Kerjakan dengan sadar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(horizontalAlignment = Alignment.End) { Text(dayNumber, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black); Text(monthName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(weekday, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp); Text("$done dari ${todayTasks.size} agenda selesai", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxSize(), strokeWidth = 6.dp, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant); Text("$percent%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) { Column(Modifier.weight(1f)) { Text(active.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("TERSISA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }; Column(Modifier.weight(1f)) { Text(done.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("SELESAI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }; Column(Modifier.weight(1f)) { Text(todayTasks.count { it.priority == "Mendesak" && it.status != "Selesai" }.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("MENDESAK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) { Column(Modifier.weight(1f)) { Text("TODAY FLOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp); Text("Alur aktivitas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }; Text("${todayTasks.size} agenda", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (todayTasks.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.WbSunny, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary); Text("Ruang harimu masih kosong", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Jadwalkan tugas pada hari ini agar muncul di sini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(todayTasks, key = { it.id }) { task ->
                Row(Modifier.fillMaxWidth().clickable { onEdit(task) }.padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(52.dp), horizontalAlignment = Alignment.Start) { Text(task.scheduledStartAt?.let(::formatTime) ?: "ALL DAY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); task.scheduledEndAt?.let { Text(formatTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (task.status == "Selesai") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text("${task.category.uppercase(Locale("id", "ID"))}  /  ${task.priority.uppercase(Locale("id", "ID"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.3.sp)
                        if (task.deadlineAt != null) Text("DEADLINE ${formatDate(task.deadlineAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(task.status.uppercase(Locale("id", "ID")), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (task.status == "Selesai") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) { IconButton(onClick = { if (task.status != "Selesai") onComplete(task) }, enabled = task.status != "Selesai") { Icon(if (task.status == "Selesai") Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = "Tandai selesai", tint = if (task.status == "Selesai") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { onDelete(task) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
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
    val overdueCount = filtered.count { it.deadlineAt != null && it.deadlineAt < LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() && it.status != "Selesai" && it.status != "Dibatalkan" }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("TASK LIBRARY / 04", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold); Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Tangkap, susun, lalu tuntaskan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(horizontalAlignment = Alignment.End) { Text(filtered.size.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black); Text("TOTAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) { Column(Modifier.weight(1f)) { Text(activeCount.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("AKTIF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }; Column(Modifier.weight(1f)) { Text(filtered.count { it.status == "Selesai" }.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("SELESAI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }; Column(Modifier.weight(1f)) { Text(overdueCount.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); Text("TERLAMBAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) } }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box { FilterChip(selected = statusFilter != "Semua", onClick = { filterMenu = "status" }, label = { Text("Status") }); DropdownMenu(filterMenu == "status", { filterMenu = "" }) { statuses.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { statusFilter = value; filterMenu = "" }) } } }
            Box { FilterChip(selected = categoryFilter != "Semua", onClick = { filterMenu = "category" }, label = { Text("Kategori") }); DropdownMenu(filterMenu == "category", { filterMenu = "" }) { categories.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { categoryFilter = value; filterMenu = "" }) } } }
            Box { FilterChip(selected = priorityFilter != "Semua", onClick = { filterMenu = "priority" }, label = { Text("Prioritas") }); DropdownMenu(filterMenu == "priority", { filterMenu = "" }) { priorities.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { priorityFilter = value; filterMenu = "" }) } } }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { sortByDeadline = !sortByDeadline }) { Icon(Icons.Outlined.Sort, contentDescription = null); Spacer(Modifier.width(5.dp)); Text(if (sortByDeadline) "Deadline terdekat" else "Urutan terbaru") }; Spacer(Modifier.weight(1f)); TextButton(onClick = { statusFilter = "Semua"; categoryFilter = "Semua"; priorityFilter = "Semua"; sortByDeadline = false }) { Text("Reset") } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (filtered.isEmpty()) EmptyState(if (tasks.isEmpty()) emptyTitle else "Tidak ada hasil", if (tasks.isEmpty()) emptyDescription else "Ubah atau reset filter untuk melihat tugas lain.")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(filtered, key = { it.id }) { task ->
                Row(Modifier.fillMaxWidth().clickable { onEdit(task) }.padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(8.dp), color = when (task.priority) { "Mendesak" -> MaterialTheme.colorScheme.errorContainer; "Tinggi" -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }) { Text(when (task.priority) { "Mendesak" -> "!!"; "Tinggi" -> "↑"; else -> "·" }, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (task.status == "Selesai") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text("${task.category.uppercase(Locale("id", "ID"))}  /  ${task.status.uppercase(Locale("id", "ID"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.3.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { task.deadlineAt?.let { Text("DEADLINE ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }; task.scheduledStartAt?.let { Text(formatTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
                    }
                    Column(horizontalAlignment = Alignment.End) { IconButton(onClick = { if (task.status != "Selesai" && task.status != "Dibatalkan") onComplete(task) }, enabled = task.status != "Selesai" && task.status != "Dibatalkan") { Icon(if (task.status == "Selesai") Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = "Tandai selesai", tint = if (task.status == "Selesai") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { onDelete(task) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus tugas", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun MoreMenu(modifier: Modifier = Modifier, onHabitClick: () -> Unit, onNotesClick: () -> Unit, onBrainDumpClick: () -> Unit, onSmartAssistantClick: () -> Unit, onAnalyticsClick: () -> Unit, onSettingsClick: () -> Unit, onBackupClick: () -> Unit, onDailyPlanningClick: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("WORKSPACE / 05", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp); Text("Ruang kerja", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Alat bantu untuk mengelola ritme dan informasi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PLAN & REFLECT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            WorkspaceRow("Daily Planning", "Susun waktu dan kapasitas harian", Icons.Outlined.Today, onDailyPlanningClick)
            WorkspaceRow("Smart Assistant", "Analisis risiko dan rekomendasi offline", Icons.Outlined.AutoAwesome, onSmartAssistantClick)
            WorkspaceRow("Analytics", "Ringkasan produktivitas dan kebiasaan", Icons.Outlined.Analytics, onAnalyticsClick)
            WorkspaceRow("Habit Tracker", "Pantau konsistensi kebiasaan", Icons.Outlined.CheckCircle, onHabitClick)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CAPTURE & ORGANIZE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            WorkspaceRow("Notes", "Catatan dan ide penting", Icons.Outlined.StickyNote2, onNotesClick)
            WorkspaceRow("Brain Dump", "Tangkap pikiran tanpa struktur", Icons.Outlined.Lightbulb, onBrainDumpClick)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("APP & DATA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            WorkspaceRow("Backup & Restore", "Simpan atau pulihkan data melalui JSON", Icons.Outlined.CloudUpload, onBackupClick)
            WorkspaceRow("Settings", "Tema dan preferensi aplikasi", Icons.Outlined.Settings, onSettingsClick)
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun WorkspaceRow(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Icon(icon, contentDescription = null, modifier = Modifier.padding(11.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Outlined.ArrowOutward, contentDescription = "Buka $title", tint = MaterialTheme.colorScheme.primary)
    }
}


private data class ProtectedTimeEntry(val start: String, val end: String, val name: String)

private fun parseProtectedTimeEntries(raw: String): List<ProtectedTimeEntry> =
    raw.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.split("|", limit = 2)
        val times = parts.firstOrNull()?.split("-", limit = 2) ?: emptyList()
        if (parts.size != 2 || times.size != 2) null
        else ProtectedTimeEntry(times[0].trim(), times[1].trim(), parts[1].trim())
    }.toList()

@Composable
private fun SettingsScreen(
    themeMode: String,
    productiveHours: Int,
    protectedTimes: String,
    snoozeMinutes: Int,
    workdayStart: String,
    workdayEnd: String,
    categories: List<String>,
    pinConfigured: Boolean,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onThemeChange: (String) -> Unit,
    onProductiveHoursChange: (Int) -> Unit,
    onProtectedTimesChange: (String) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
    onWorkdayChange: (String, String) -> Unit,
    onCategoriesChange: (List<String>) -> Unit
) {
    var workdayStartInput by remember(workdayStart) { mutableStateOf(workdayStart) }
    var workdayEndInput by remember(workdayEnd) { mutableStateOf(workdayEnd) }
    var workdayError by remember { mutableStateOf<String?>(null) }
    var hoursInput by remember(productiveHours) { mutableStateOf(productiveHours.toString()) }
    val protectedEntries = remember(protectedTimes) { mutableStateListOf<ProtectedTimeEntry>().apply { addAll(parseProtectedTimeEntries(protectedTimes)) } }
    var protectedError by remember { mutableStateOf<String?>(null) }
    var selectedSnooze by remember(snoozeMinutes) { mutableIntStateOf(snoozeMinutes) }
    var categoryInput by remember { mutableStateOf("") }
    var categoryError by remember { mutableStateOf<String?>(null) }
    val options = listOf("system" to "Ikuti sistem", "light" to "Terang", "dark" to "Gelap")

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali")
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        val notificationContext = LocalContext.current
        var notificationPermissionRefresh by remember { mutableIntStateOf(0) }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            notificationPermissionRefresh++
        }
        val notificationPermissionGranted = notificationPermissionRefresh >= 0 && (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(notificationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Izin notifikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (notificationPermissionGranted)
                        "Izin notifikasi aktif. Pengingat tugas dapat ditampilkan sesuai pengaturan Android."
                    else
                        "Izin notifikasi belum diberikan. Pengingat terjadwal tetap tersimpan, tetapi Android dapat memblokir tampilannya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= 33) {
                    Button(
                        onClick = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Izinkan notifikasi") }
                }
            }
        }

        AppSecuritySettingsCard(pinConfigured = pinConfigured, onSetPin = onSetPin, onRemovePin = onRemovePin)

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tampilan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pilih tema aplikasi. Perubahan disimpan di perangkat dan diterapkan langsung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onThemeChange(value) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeMode == value, onClick = { onThemeChange(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kapasitas produktif harian", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Batas waktu untuk pekerjaan dan tugas setiap hari. Nilai ini menjadi acuan rekomendasi Smart Assistant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Text(
                    "Rentang yang diizinkan: 1–16 jam. Pengaturan ini belum otomatis menjadwalkan ulang tugas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rentang jam kerja", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Daily Planning hanya akan menempatkan usulan tugas di dalam rentang ini. Protected Time dan jadwal yang sudah ada tetap dihindari.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimePickerField("Mulai", workdayStartInput, {
                        workdayStartInput = it
                        workdayError = null
                    }, modifier = Modifier.weight(1f))
                    TimePickerField("Selesai", workdayEndInput, {
                        workdayEndInput = it
                        workdayError = null
                    }, modifier = Modifier.weight(1f))
                }
                workdayError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                val parsedStart = runCatching { LocalTime.parse(workdayStartInput) }.getOrNull()
                val parsedEnd = runCatching { LocalTime.parse(workdayEndInput) }.getOrNull()
                Button(
                    enabled = parsedStart != null && parsedEnd != null && parsedStart < parsedEnd &&
                        (workdayStartInput != workdayStart || workdayEndInput != workdayEnd),
                    onClick = {
                        if (parsedStart != null && parsedEnd != null && parsedStart < parsedEnd) {
                            onWorkdayChange(parsedStart.toString(), parsedEnd.toString())
                            workdayError = null
                        } else workdayError = "Gunakan format HH:mm dan pastikan jam selesai setelah jam mulai."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan rentang jam kerja") }
                Text("Pilih jam melalui pemilih waktu. Jam selesai harus lebih akhir daripada jam mulai dan berada pada hari yang sama.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Durasi tunda pengingat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Atur berapa lama pengingat ditunda ketika tombol Tunda ditekan pada notifikasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(15, 30, 60, 120).forEach { minutes ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedSnooze = minutes },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedSnooze == minutes, onClick = { selectedSnooze = minutes })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (minutes < 60) "$minutes menit"
                            else if (minutes == 60) "1 jam" else "2 jam"
                        )
                    }
                }
                Button(
                    onClick = { onSnoozeMinutesChange(selectedSnooze) },
                    enabled = selectedSnooze != snoozeMinutes,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan durasi tunda") }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Protected Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Atur waktu yang dilindungi agar tidak dipakai oleh usulan Daily Planning. Blok yang melewati tengah malam tetap didukung.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (protectedEntries.isEmpty()) {
                    Text("Belum ada waktu terlindungi.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                protectedEntries.forEachIndexed { index, entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Blok ${index + 1}", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { protectedEntries.removeAt(index); protectedError = null }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus waktu terlindungi")
                            }
                        }
                        OutlinedTextField(value = entry.name, onValueChange = { value ->
                            protectedEntries[index] = entry.copy(name = value)
                            protectedError = null
                        }, label = { Text("Nama aktivitas") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimePickerField("Mulai", entry.start, { value ->
                                protectedEntries[index] = entry.copy(start = value); protectedError = null
                            }, modifier = Modifier.weight(1f))
                            TimePickerField("Selesai", entry.end, { value ->
                                protectedEntries[index] = entry.copy(end = value); protectedError = null
                            }, modifier = Modifier.weight(1f))
                        }
                    }
                    if (index < protectedEntries.lastIndex) HorizontalDivider()
                }
                OutlinedButton(onClick = {
                    protectedEntries.add(ProtectedTimeEntry("12:00", "13:00", "Aktivitas pribadi"))
                    protectedError = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah waktu terlindungi")
                }
                protectedError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        val valid = protectedEntries.all { entry ->
                            entry.name.isNotBlank() &&
                                runCatching { LocalTime.parse(entry.start); LocalTime.parse(entry.end) }.isSuccess &&
                                entry.start != entry.end
                        }
                        if (valid) {
                            onProtectedTimesChange(protectedEntries.joinToString("\n") { "${it.start}-${it.end}|${it.name.trim()}" })
                            protectedError = null
                        } else protectedError = "Lengkapi nama dan pilih jam mulai serta selesai yang berbeda pada setiap blok."
                    },
                    enabled = protectedEntries.toList() != parseProtectedTimeEntries(protectedTimes),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simpan Protected Time") }
                Text("Contoh: makan, ibadah, istirahat, dan waktu keluarga. Jam selesai lebih awal dari jam mulai berarti blok berlanjut ke hari berikutnya.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kategori tugas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Kelola kategori untuk mengelompokkan pekerjaan sekolah, urusan pribadi, dan aktivitas lain. Kategori yang sudah dipakai tugas tidak menghapus data tugas ketika dihapus dari daftar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                categories.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(item, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            if (categories.size > 1) onCategoriesChange(categories - item)
                            else categoryError = "Minimal harus ada satu kategori."
                        }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus kategori $item") }
                    }
                }
                OutlinedTextField(
                    value = categoryInput,
                    onValueChange = { categoryInput = it; categoryError = null },
                    label = { Text("Nama kategori baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                categoryError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        val name = categoryInput.trim()
                        when {
                            name.isBlank() -> categoryError = "Nama kategori tidak boleh kosong."
                            name.contains("\n") -> categoryError = "Nama kategori tidak boleh berisi baris baru."
                            categories.any { it.equals(name, ignoreCase = true) } -> categoryError = "Kategori sudah tersedia."
                            else -> { onCategoriesChange(categories + name); categoryInput = ""; categoryError = null }
                        }
                    },
                    enabled = categoryInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tambah kategori") }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tentang PaDayFlow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Versi 0.1.0 · Aplikasi produktivitas pribadi · Offline-first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun SmartAssistantScreen(tasks: List<TaskEntity>, productiveHours: Int, modifier: Modifier = Modifier, onBack: () -> Unit, onOpenTask: (TaskEntity) -> Unit) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val startToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endSoon = today.plusDays(4).atStartOfDay(zone).toInstant().toEpochMilli()
    val active = tasks.filter { it.status != "Selesai" && it.status != "Dibatalkan" }
    val overdue = active.filter { it.deadlineAt != null && it.deadlineAt < startToday }
    val dueSoon = active.filter { it.deadlineAt != null && it.deadlineAt >= startToday && it.deadlineAt < endSoon }
    val urgent = active.filter { it.priority == "Mendesak" || it.priority == "Tinggi" }
    val noSchedule = active.filter { it.startAt == null && it.deadlineAt == null }
    val todayTasks = active.filter { it.startAt?.let { v -> Instant.ofEpochMilli(v).atZone(zone).toLocalDate() == today } == true }
    val plannedMinutes = todayTasks.sumOf { it.estimatedMinutes.coerceAtLeast(0) }
    val capacityMinutes = productiveHours.coerceIn(1, 16) * 60
    val capacityPercent = plannedMinutes * 100 / capacityMinutes
    val suggestions = buildList {
        if (plannedMinutes > capacityMinutes) add("Beban terjadwal melebihi kapasitas: ${plannedMinutes / 60} jam ${plannedMinutes % 60} menit dari batas ${productiveHours} jam. Tinjau prioritas dan pertimbangkan memindahkan sebagian tugas.")
        else if (plannedMinutes >= capacityMinutes * 80 / 100) add("Kapasitas hari ini hampir penuh: estimasi penggunaan ${capacityPercent}%. Sisakan ruang untuk jeda dan pekerjaan tak terduga.")
        overdue.forEach { add("Terlambat: ${it.title}. Tinjau progres dan tentukan tindak lanjut.") }
        dueSoon.forEach { add("Deadline mendekat: ${it.title}. Sisihkan waktu sebelum ${formatDate(it.deadlineAt!!)}.") }
        urgent.filter { t -> overdue.none { it.id == t.id } && dueSoon.none { it.id == t.id } }.forEach { add("Prioritas tinggi: ${it.title}. Pertimbangkan untuk mengerjakannya lebih awal.") }
        noSchedule.take(5).forEach { add("Belum dijadwalkan: ${it.title}. Tetapkan tanggal mulai atau deadline.") }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }; Column(Modifier.weight(1f)) { Text("INTELLIGENCE / 06", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text("Smart Assistant", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Analisis lokal. Keputusan tetap di tanganmu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) { Column(Modifier.weight(1f)) { Text(active.size.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("AKTIF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }; Column(Modifier.weight(1f)) { Text(overdue.size.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = if (overdue.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); Text("TERLAMBAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }; Column(Modifier.weight(1f)) { Text(dueSoon.size.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("DEADLINE DEKAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("CAPACITY / 01", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text("Beban hari ini", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) { Text("${plannedMinutes / 60}j ${plannedMinutes % 60}m", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)); Text("dari ${productiveHours} jam", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; LinearProgressIndicator(progress = { (plannedMinutes.toFloat() / capacityMinutes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = if (plannedMinutes > capacityMinutes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant); Text("$capacityPercent% kapasitas terpakai · ${todayTasks.size} tugas dengan tanggal mulai hari ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Estimasi hanya menghitung durasi tugas yang memiliki tanggal mulai hari ini. Tugas tanpa estimasi dihitung 0 menit.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("SIGNALS / 02", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text("Sinyal yang perlu diperhatikan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); if (suggestions.isEmpty()) EmptyState("Belum ada risiko yang terdeteksi", "Tetap periksa daftar tugas dan pastikan tanggal serta prioritas diperbarui.") else suggestions.forEachIndexed { index, suggestion -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) { Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black); Text(suggestion, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("REVIEW / 03", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text("Perlu ditinjau", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); val review = (overdue + dueSoon + urgent.filter { t -> overdue.none { it.id == t.id } && dueSoon.none { it.id == t.id } }).distinctBy { it.id }; if (review.isEmpty()) Text("Tidak ada tugas prioritas yang perlu ditinjau.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else review.take(10).forEach { task -> Row(Modifier.fillMaxWidth().clickable { onOpenTask(task) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(task.title, fontWeight = FontWeight.Bold); Text("${task.priority} · ${task.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Outlined.ArrowOutward, contentDescription = "Buka tugas", tint = MaterialTheme.colorScheme.primary) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) } }
        Text("Rekomendasi bersifat informatif. Tidak ada tugas, jadwal, atau status yang diubah otomatis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
    }
}


@Composable
private fun NotesScreen(dao: NoteDao, type: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val notes by dao.observeByType(type).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val isBrainDump = type == "BRAIN_DUMP"
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }; Column(Modifier.weight(1f)) { Text(if (isBrainDump) "CAPTURE / 09" else "KNOWLEDGE / 08", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text(if (isBrainDump) "Brain Dump" else "Notes", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text(if (isBrainDump) "Tangkap pikiran sebelum menghilang." else "Simpan hal yang layak diingat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, contentDescription = "Tambah catatan") } }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(notes.size.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text(if (isBrainDump) "IDE TERSIMPAN" else "CATATAN TERSIMPAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.2.sp) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        if (notes.isEmpty()) EmptyState(if (isBrainDump) "Belum ada ide" else "Belum ada catatan", "Tekan tombol + untuk mulai menulis.") else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(notes, key = { it.id }) { note ->
                Column(Modifier.fillMaxWidth().clickable { editing = note }.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(note.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { dao.delete(note) } } }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } }
                    if (note.content.isNotBlank()) Text(note.content, maxLines = 4, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("DIPERBARUI  ${Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("id", "ID")))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.4.sp)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (adding) NoteEditorDialog(null, isBrainDump, onDismiss = { adding = false }, onSave = { title, content -> scope.launch { withContext(Dispatchers.IO) { val now = System.currentTimeMillis(); dao.insert(NoteEntity(title = title, content = content, type = type, createdAt = now, updatedAt = now)) } }; adding = false })
    editing?.let { note -> NoteEditorDialog(note, isBrainDump, onDismiss = { editing = null }, onSave = { title, content -> scope.launch { withContext(Dispatchers.IO) { dao.update(note.copy(title = title, content = content, updatedAt = System.currentTimeMillis())) } }; editing = null }) }
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
private fun HabitTrackerScreen(dao: HabitDao, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val habits by dao.observeHabits().collectAsState(initial = emptyList())
    val logs by dao.observeLogs().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var habitName by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("7") }
    val today = LocalDate.now()
    val todayKey = today.toString()
    val weekStart = today.with(java.time.DayOfWeek.MONDAY)
    val completedToday = habits.count { h -> logs.any { it.habitId == h.id && it.date == todayKey && it.completed } }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }; Column(Modifier.weight(1f)) { Text("RHYTHM / 07", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text("Habit Tracker", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Konsistensi dibangun dari pengulangan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, contentDescription = "Tambah kebiasaan") } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) { Column(Modifier.weight(1f)) { Text(habits.size.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("KEBIASAAN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) }; Column(Modifier.weight(1f)) { Text(completedToday.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Text("SELESAI HARI INI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("WEEKLY CHECK-IN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold); Text("Ritme minggu ini", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }; Text(today.format(DateTimeFormatter.ofPattern("d MMM", Locale("id", "ID"))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (habits.isEmpty()) EmptyState("Belum ada kebiasaan", "Tambahkan kebiasaan pertama, misalnya membaca, olahraga, atau minum air.") else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(habits, key = { it.id }) { habit ->
                val completedDates = logs.filter { it.habitId == habit.id && it.completed }.map { it.date }.toSet()
                val doneToday = todayKey in completedDates
                val weeklyCount = (0..6).count { weekStart.plusDays(it.toLong()).toString() in completedDates }
                val streak = calculateHabitStreak(completedDates, today)
                var menuExpanded by remember(habit.id) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("$weeklyCount / ${habit.targetPerWeek} target minggu ini · streak $streak hari", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Box { IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Menu kebiasaan") }; DropdownMenu(menuExpanded, { menuExpanded = false }) { DropdownMenuItem(text = { Text("Hapus kebiasaan") }, onClick = { menuExpanded = false; scope.launch { withContext(Dispatchers.IO) { dao.deleteLogsForHabit(habit.id); dao.deleteHabit(habit.id) } } }) } } }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(7) { index -> val dateKey = weekStart.plusDays(index.toLong()).toString(); val checked = dateKey in completedDates; Column(Modifier.weight(1f).clickable { scope.launch { withContext(Dispatchers.IO) { if (checked) dao.removeLog(habit.id, dateKey) else dao.saveLog(HabitLogEntity(habit.id, dateKey)) } } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(listOf("S", "S", "R", "K", "J", "S", "M")[index], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Surface(Modifier.fillMaxWidth().height(30.dp), shape = RoundedCornerShape(7.dp), color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { if (checked) Icon(Icons.Outlined.Check, contentDescription = "Selesai", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) } } } } }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(if (doneToday) "Target hari ini tercapai" else "Belum dicentang hari ini", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); FilledTonalButton(onClick = { scope.launch { withContext(Dispatchers.IO) { if (doneToday) dao.removeLog(habit.id, todayKey) else dao.saveLog(HabitLogEntity(habit.id, todayKey)) } } }) { Text(if (doneToday) "Batalkan" else "Selesaikan") } }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Tambah kebiasaan") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(habitName, { habitName = it }, label = { Text("Nama kebiasaan") }, singleLine = true); OutlinedTextField(target, { target = it.filter(Char::isDigit).take(1) }, label = { Text("Target per minggu (1–7)") }, singleLine = true) } }, confirmButton = { Button(enabled = habitName.isNotBlank() && (target.toIntOrNull() ?: 0) in 1..7, onClick = { scope.launch { withContext(Dispatchers.IO) { dao.insertHabit(HabitEntity(name = habitName.trim(), targetPerWeek = target.toInt())) } }; habitName = ""; target = "7"; adding = false }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { adding = false }) { Text("Batal") } })
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
    fun dates(task: TaskEntity) = buildSet { task.startAt?.let { add(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }; task.scheduledStartAt?.let { add(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }; task.deadlineAt?.let { add(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) } }
    fun hasItems(date: LocalDate) = tasks.any { date in dates(it) }
    val selected = tasks.filter { selectedDate in dates(it) }.distinctBy { it.id }.sortedBy { it.scheduledStartAt ?: it.startAt ?: it.deadlineAt ?: Long.MAX_VALUE }
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val week = (0..6).map { weekStart.plusDays(it.toLong()) }
    val agenda = tasks.filter { task -> dates(task).any { !it.isBefore(today) && !it.isAfter(today.plusDays(30)) } }.distinctBy { it.id }.sortedBy { it.scheduledStartAt ?: it.startAt ?: it.deadlineAt ?: Long.MAX_VALUE }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("PLANNER / 03", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold); Text("Kalender", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("Lihat waktu. Tentukan arah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(horizontalAlignment = Alignment.End) { Text(today.dayOfMonth.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary); Text(today.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale("id", "ID"))).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) { listOf("Bulan", "Minggu", "Agenda").forEach { option -> val active = view == option; Column(Modifier.weight(1f).clickable { view = option }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(option.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (active) FontWeight.Black else FontWeight.Normal, letterSpacing = 0.8.sp); Surface(Modifier.fillMaxWidth().height(if (active) 3.dp else 1.dp), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) {} } } }
        if (view != "Agenda") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (view == "Bulan") { month = month.minusMonths(1); selectedDate = month } else selectedDate = selectedDate.minusWeeks(1) }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Sebelumnya") }; Text(if (view == "Bulan") month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))).uppercase() else "${weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale("id", "ID")))} — ${weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("id", "ID")))}", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black); IconButton(onClick = { if (view == "Bulan") { month = month.plusMonths(1); selectedDate = month } else selectedDate = selectedDate.plusWeeks(1) }) { Icon(Icons.Outlined.ArrowForward, contentDescription = "Berikutnya") } }
            if (view == "Bulan") {
                Row(Modifier.fillMaxWidth()) { listOf("SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN").forEach { day -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) } } }
                val cellCount = ((offset + days + 6) / 7) * 7
                for (row in 0 until cellCount step 7) { Row(Modifier.fillMaxWidth()) { for (col in 0..6) { val number = row + col - offset + 1; if (number in 1..days) { val date = month.withDayOfMonth(number); val chosen = date == selectedDate; val isToday = date == today; Column(Modifier.weight(1f).height(56.dp).clickable { selectedDate = date }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { Surface(shape = RoundedCornerShape(12.dp), color = if (chosen) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) { Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(number.toString(), fontWeight = if (chosen || isToday) FontWeight.Black else FontWeight.Normal, color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }; if (hasItems(date)) Surface(Modifier.size(5.dp), shape = RoundedCornerShape(50), color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) {} else Spacer(Modifier.height(5.dp)) } } else Spacer(Modifier.weight(1f).height(56.dp)) } } }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { week.forEach { date -> val chosen = date == selectedDate; Column(Modifier.weight(1f).clickable { selectedDate = date }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("id", "ID")).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Surface(shape = RoundedCornerShape(14.dp), color = if (chosen) MaterialTheme.colorScheme.primary else if (date == today) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) { Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { Text(date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, color = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }; if (hasItems(date)) Surface(Modifier.size(5.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiary) {} else Spacer(Modifier.height(5.dp)) } } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("●  Ada aktivitas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { month = today.withDayOfMonth(1); selectedDate = today }) { Text("Hari ini") } }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("30 HARI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text("Agenda mendatang", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }; Text("${agenda.size} item", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (agenda.isEmpty()) EmptyState("Belum ada agenda", "Tidak ada jadwal atau deadline dalam 30 hari ke depan.") else agenda.forEach { task -> Row(Modifier.fillMaxWidth().clickable { onEdit(task) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.width(54.dp)) { Text(task.scheduledStartAt?.let(::formatTime) ?: "ALL DAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); task.deadlineAt?.let { Text("DUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) } }; Column(Modifier.weight(1f)) { Text(task.title, fontWeight = FontWeight.Bold); Text("${task.category} · ${task.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Outlined.ArrowOutward, contentDescription = null) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) { Column(Modifier.weight(1f)) { Text("SELECTED DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold); Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }; Text("${selected.size} aktivitas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (selected.isEmpty()) Text("Tidak ada aktivitas pada tanggal ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else selected.forEach { task -> Row(Modifier.fillMaxWidth().clickable { onEdit(task) }.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) { Column(Modifier.width(54.dp)) { Text(task.scheduledStartAt?.let(::formatTime) ?: "— —", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); task.scheduledEndAt?.let { Text(formatTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(task.title, fontWeight = FontWeight.Bold); Text("${task.category} · ${task.priority} · ${task.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (task.deadlineAt != null) Text("DEADLINE ${formatDate(task.deadlineAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) }; Icon(Icons.Outlined.ArrowOutward, contentDescription = "Buka tugas", tint = MaterialTheme.colorScheme.primary) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
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
private fun TaskEditorDialog(task: TaskEntity?, categories: List<String>, onDismiss: () -> Unit, onSave: (TaskEntity) -> Unit) {
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
                        (categories + category).distinct().forEach {
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
                TimePickerField("Jam mulai", scheduledStartTime, { scheduledStartTime = it },
                    "Gunakan tanggal mulai di atas.")
                TimePickerField("Jam selesai", scheduledEndTime, { scheduledEndTime = it },
                    "Pilih waktu selesai setelah jam mulai.")
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
                TimePickerField("Jam pengingat", reminderTime, { reminderTime = it },
                    "Opsional. Jika kosong, pengingat dijadwalkan pukul 09:00.")
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
private fun TimePickerField(label: String, value: String, onTimeSelected: (String) -> Unit, supportingText: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val parsedTime = remember(value) { runCatching { LocalTime.parse(value) }.getOrNull() }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("Pilih jam") },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onTimeSelected("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Hapus jam")
                    }
                }
                IconButton(onClick = {
                    val now = LocalTime.now()
                    android.app.TimePickerDialog(
                        context,
                        { _, hour, minute -> onTimeSelected(String.format(Locale.ROOT, "%02d:%02d", hour, minute)) },
                        parsedTime?.hour ?: now.hour,
                        parsedTime?.minute ?: now.minute,
                        true
                    ).show()
                }) {
                    Icon(Icons.Outlined.Schedule, contentDescription = "Pilih jam")
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth().clickable {
            val now = LocalTime.now()
            android.app.TimePickerDialog(
                context,
                { _, hour, minute -> onTimeSelected(String.format(Locale.ROOT, "%02d:%02d", hour, minute)) },
                parsedTime?.hour ?: now.hour,
                parsedTime?.minute ?: now.minute,
                true
            ).show()
        }
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
