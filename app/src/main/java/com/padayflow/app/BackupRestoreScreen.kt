package com.padayflow.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.padayflow.app.data.BackupManager
import com.padayflow.app.data.PaDayFlowDatabase
import com.padayflow.app.data.ParsedBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun BackupRestoreScreen(
    database: PaDayFlowDatabase,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var parsedBackup by remember { mutableStateOf<ParsedBackup?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = BackupManager.createBackup(database)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("Tidak dapat menulis file tujuan.")
                }
            }
            message = if (result.isSuccess) "Backup berhasil disimpan." else "Backup gagal: ${result.exceptionOrNull()?.message ?: "kesalahan tidak diketahui"}"
            busy = false
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            parsedBackup = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("File tidak dapat dibaca.")
                    BackupManager.parseBackup(json)
                }
            }
            parsedBackup = result.getOrNull()
            if (result.isFailure) message = "File tidak valid: ${result.exceptionOrNull()?.message ?: "kesalahan tidak diketahui"}"
            busy = false
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Kembali") }
            Text("Backup & Restore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Buat backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Simpan salinan tugas, habit, riwayat habit, dan catatan ke file JSON. Simpan file di tempat aman atau media penyimpanan lain.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    val filename = "PaDayFlow-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json"
                    createDocument.launch(filename)
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Buat file backup") }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Pulihkan backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Pilih file backup PaDayFlow. Isi file akan diperiksa terlebih dahulu sebelum data lama diganti.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { openDocument.launch(arrayOf("application/json", "text/json", "application/octet-stream")) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Pilih file backup") }
            }
        }
        if (busy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }
        message?.let {
            Text(it, color = if (it.startsWith("Backup berhasil")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
        Text("Catatan: proses restore mengganti seluruh data tugas, habit, log habit, dan catatan yang saat ini tersimpan. Buat backup terbaru sebelum memulihkan file lama.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    parsedBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { parsedBackup = null },
            title = { Text("Konfirmasi pemulihan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("File backup valid. Data yang akan dipulihkan:")
                    Text("• ${backup.summary.taskCount} tugas")
                    Text("• ${backup.summary.habitCount} habit")
                    Text("• ${backup.summary.habitLogCount} riwayat habit")
                    Text("• ${backup.summary.noteCount} catatan")
                    Spacer(Modifier.height(6.dp))
                    Text("Seluruh data saat ini akan diganti. Tindakan ini tidak dapat dibatalkan.",
                        color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        val result = runCatching { withContext(Dispatchers.IO) { BackupManager.restore(database, backup) } }
                        message = if (result.isSuccess) "Restore berhasil. Data lokal telah diganti sesuai file backup."
                            else "Restore gagal: ${result.exceptionOrNull()?.message ?: "kesalahan tidak diketahui"}"
                        parsedBackup = null
                        busy = false
                    }
                }) { Text("Pulihkan data") }
            },
            dismissButton = { TextButton(onClick = { parsedBackup = null }) { Text("Batal") } }
        )
    }
}
