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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tabs = listOf("Beranda", "My Day", "Tugas", "Kalender", "Lainnya")
private val tabIcons = listOf(Icons.Outlined.Home, Icons.Outlined.Today, Icons.Outlined.TaskAlt, Icons.Outlined.CalendarMonth, Icons.Outlined.MoreHoriz)

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
    var adding by remember { mutableStateOf(false) }
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
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) {
            Icon(Icons.Outlined.Add, contentDescription = "Tambah tugas")
        } }
    ) { padding ->
        when (tab) {
            0 -> Dashboard(tasks, Modifier.padding(padding))
            1, 2 -> TaskList(tasks, Modifier.padding(padding), onComplete = { task ->
                scope.launch { withContext(Dispatchers.IO) { dao.updateStatus(task.id, "Selesai") } }
            })
            else -> Placeholder(tabs[tab], Modifier.padding(padding))
        }
    }
    if (adding) AddTaskDialog(onDismiss = { adding = false }, onSave = { title, category, priority ->
        scope.launch {
            withContext(Dispatchers.IO) { dao.insert(TaskEntity(title = title, category = category, priority = priority)) }
            adding = false
            tab = 2
        }
    })
}

@Composable
private fun Dashboard(tasks: List<TaskEntity>, modifier: Modifier = Modifier) {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))
    val done = tasks.count { it.status == "Selesai" }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PaDayFlow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(date.replaceFirstChar { it.uppercase(Locale("id", "ID")) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Selamat datang.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Susun prioritas, lalu kerjakan satu per satu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ringkasan tugas", fontWeight = FontWeight.SemiBold)
                Text(done.toString() + " selesai · " + (tasks.size - done).toString() + " belum selesai",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(tasks.size.toString() + " tugas tersimpan di perangkat")
            }
        }
        Heading("Agenda hari ini", "Tugas terbaru")
        if (tasks.isEmpty()) EmptyState("Belum ada agenda", "Tekan tombol + untuk menambahkan tugas pertama.")
        else tasks.take(3).forEach { TaskCard(it) {} }
        Heading("Prioritas utama", "Tugas tinggi dan mendesak")
        val priorities = tasks.filter { it.priority == "Tinggi" || it.priority == "Mendesak" }.take(3)
        if (priorities.isEmpty()) EmptyState("Belum ada prioritas tinggi", "Tandai tugas penting dengan prioritas Tinggi atau Mendesak.")
        else priorities.forEach { TaskCard(it) {} }
        Spacer(Modifier.height(70.dp))
    }
}

@Composable
private fun TaskList(tasks: List<TaskEntity>, modifier: Modifier = Modifier, onComplete: (TaskEntity) -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Semua tugas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(tasks.size.toString() + " tugas tersimpan", modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (tasks.isEmpty()) EmptyState("Belum ada tugas", "Tekan tombol + untuk membuat tugas.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tasks, key = { it.id }) { task -> TaskCard(task) { onComplete(task) } }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onComplete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                Text(task.category + " · " + task.priority, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(task.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (task.status != "Selesai") TextButton(onClick = onComplete) { Text("Selesai") }
        }
    }
}

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

@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pribadi") }
    var priority by remember { mutableStateOf("Normal") }
    var categoryMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah tugas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nama tugas") }, singleLine = true)
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
            }
        },
        confirmButton = { Button(onClick = { onSave(title.trim(), category, priority) }, enabled = title.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
