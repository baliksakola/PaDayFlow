package com.padayflow.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val PIN_ITERATIONS = 150_000
private const val PIN_KEY_LENGTH = 256

internal fun createPinSalt(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

internal fun hashAppPin(pin: String, saltHex: String): String {
    val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_ITERATIONS, PIN_KEY_LENGTH)
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            .joinToString("") { "%02x".format(it) }
    } finally {
        spec.clearPassword()
    }
}

internal fun verifyAppPin(pin: String, salt: String, expectedHash: String): Boolean {
    if (pin.length != 6 || salt.isBlank() || expectedHash.isBlank()) return false
    val actual = hashAppPin(pin, salt)
    return MessageDigest.isEqual(
        actual.toByteArray(Charsets.US_ASCII),
        expectedHash.toByteArray(Charsets.US_ASCII)
    )
}

@Composable
internal fun AppLockScreen(
    expectedHash: String,
    salt: String,
    biometricAvailable: Boolean,
    onBiometricUnlock: () -> Unit,
    onUnlock: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("PaDayFlow terkunci", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Masukkan PIN 6 digit untuk melanjutkan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { value ->
                if (value.length <= 6 && value.all(Char::isDigit)) {
                    pin = value
                    error = null
                }
            },
            label = { Text("PIN aplikasi") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                checking = true
                scope.launch {
                    val valid = withContext(Dispatchers.Default) { verifyAppPin(pin, salt, expectedHash) }
                    checking = false
                    if (valid) onUnlock() else {
                        pin = ""
                        error = "PIN tidak sesuai."
                    }
                }
            },
            enabled = pin.length == 6 && !checking,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (checking) "Memeriksa..." else "Buka dengan PIN") }
        if (biometricAvailable) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBiometricUnlock, modifier = Modifier.fillMaxWidth()) {
                Text("Buka dengan biometrik")
            }
        }
    }
}


@Composable
internal fun AppSecuritySettingsCard(
    pinConfigured: Boolean,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit
) {
    var showSetup by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keamanan aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (pinConfigured) "Kunci PIN aktif. Aplikasi akan meminta PIN saat dibuka kembali setelah ditinggalkan."
                else "Kunci aplikasi belum aktif. Atur PIN 6 digit untuk melindungi tugas, catatan, dan data pribadi.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { showSetup = true; firstPin = ""; confirmPin = ""; error = null }, modifier = Modifier.fillMaxWidth()) {
                Text(if (pinConfigured) "Ubah PIN" else "Atur PIN")
            }
            if (pinConfigured) {
                TextButton(onClick = onRemovePin, modifier = Modifier.fillMaxWidth()) { Text("Nonaktifkan kunci aplikasi") }
            }
        }
    }

    if (showSetup) {
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text(if (pinConfigured) "Ubah PIN aplikasi" else "Atur PIN aplikasi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gunakan PIN 6 digit. PIN disimpan sebagai hash dengan salt, bukan sebagai teks biasa.")
                    OutlinedTextField(
                        value = firstPin,
                        onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) { firstPin = value; error = null } },
                        label = { Text("PIN baru") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { value -> if (value.length <= 6 && value.all(Char::isDigit)) { confirmPin = value; error = null } },
                        label = { Text("Konfirmasi PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        firstPin.length != 6 -> error = "PIN harus terdiri dari 6 digit."
                        firstPin != confirmPin -> error = "Konfirmasi PIN tidak sama."
                        else -> { onSetPin(firstPin); showSetup = false }
                    }
                }) { Text("Simpan PIN") }
            },
            dismissButton = { TextButton(onClick = { showSetup = false }) { Text("Batal") } }
        )
    }
}
