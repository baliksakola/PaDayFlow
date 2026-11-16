package com.padayflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.padayflow.app.ui.theme.PaDayFlowTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class Destination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val destinations = listOf(
    Destination("Beranda", Icons.Outlined.Home),
    Destination("My Day", Icons.Outlined.Today),
    Destination("Tugas", Icons.Outlined.TaskAlt),
    Destination("Kalender", Icons.Outlined.CalendarMonth),
    Destination("Lainnya", Icons.Outlined.MoreHoriz)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaDayFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PaDayFlowApp()
                }
            }
        }
    }
}

@Composable
private fun PaDayFlowApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddTask by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTask = true }) {
                Icon(Icons.Outlined.TaskAlt, contentDescription = "Tambah tugas")
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            DashboardScreen(Modifier.padding(innerPadding))
        } else {
            DestinationPlaceholder(
                title = destinations[selectedTab].label,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showAddTask) {
        AddTaskDialog(onDismiss = { showAddTask = false })
    }
}

@Composable
private fun DashboardScreen(modifier: Modifier = Modifier) {
    val today = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID"))
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PaDayFlow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(today.replaceFirstChar { it.uppercase(Locale("id", "ID")) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text("DY", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Selamat datang.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Susun prioritas, lalu kerjakan satu per satu.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("  Progres hari ini", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text("Belum ada tugas", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Tugas yang dijadwalkan hari ini akan muncul di sini.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        SectionHeading("Agenda hari ini", "Jadwal dan aktivitas")
        EmptyCard(
            title = "Belum ada agenda",
            description = "Tambahkan tugas atau jadwal agar aktivitas hari ini tersusun."
        )

        SectionHeading("Prioritas utama", "Fokus pada yang penting")
        EmptyCard(
            title = "Belum ada prioritas",
            description = "Tugas dengan prioritas tinggi akan ditampilkan pada bagian ini."
        )

        SectionHeading("Deadline mendatang", "Pantau tenggat waktu")
        EmptyCard(
            title = "Tidak ada deadline",
            description = "Deadline tugas akan dirangkum di sini."
        )

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DestinationPlaceholder(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Bagian ini akan dikembangkan pada tahap berikutnya.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah tugas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nama tugas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Form ini masih berupa rancangan antarmuka. Penyimpanan tugas akan dibuat saat modul database diimplementasikan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
