package com.mindflux.linuxlauncher.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindflux.linuxlauncher.runtime.LauncherErrorHistory
import com.mindflux.linuxlauncher.runtime.LauncherNotifications
import com.mindflux.linuxlauncher.runtime.LauncherPreferences
import com.mindflux.linuxlauncher.runtime.LinuxExternalFiles
import com.mindflux.linuxlauncher.runtime.LinuxSystemRegistry
import com.mindflux.linuxlauncher.runtime.LinuxUpdateChecker
import com.mindflux.linuxlauncher.runtime.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AdvancedSettingsScreen(onOpenDistro: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(LauncherPreferences.mode(context)) }
    var adaptive by remember { mutableStateOf(LauncherPreferences.adaptive(context)) }
    var cpuLevel by remember { mutableStateOf(LauncherPreferences.cpuLevel(context)) }
    var ramReserve by remember { mutableStateOf(LauncherPreferences.ramReserveMb(context)) }
    var desktopResolution by remember { mutableStateOf(LauncherPreferences.desktopResolution(context)) }
    var systems by remember { mutableStateOf(LinuxSystemRegistry.list(context)) }
    var targetDistro by remember { mutableStateOf("") }
    var sharedFiles by remember { mutableStateOf(emptyList<java.io.File>()) }
    var status by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(LauncherErrorHistory.all(context)) }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    var exportTarget by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(systems) {
        if (targetDistro !in systems) targetDistro = systems.firstOrNull().orEmpty()
    }
    LaunchedEffect(targetDistro) {
        sharedFiles = if (targetDistro.isBlank()) emptyList() else withContext(Dispatchers.IO) { LinuxExternalFiles.files(context, targetDistro) }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = targetDistro
        if (uri != null && id.isNotBlank()) scope.launch {
            status = "Copying file into Linux…"
            try {
                val file = withContext(Dispatchers.IO) { LinuxExternalFiles.importFile(context, id, uri) }
                status = "Imported ${file.name} into /root/shared"
                sharedFiles = withContext(Dispatchers.IO) { LinuxExternalFiles.files(context, id) }
            } catch (t: Throwable) { status = "Import failed: ${t.message ?: "unknown error"}"; LauncherErrorHistory.record(context, "External file import", status) }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source = exportTarget
        if (uri != null && source != null) scope.launch {
            status = "Exporting ${source.name}…"
            try { withContext(Dispatchers.IO) { LinuxExternalFiles.export(context, source, uri) }; status = "Exported ${source.name}" }
            catch (t: Throwable) { status = "Export failed: ${t.message ?: "unknown error"}"; LauncherErrorHistory.record(context, "External file export", status) }
        }
        exportTarget = null
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("PREFERENCES", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
        Text("Settings.", style = MaterialTheme.typography.headlineMedium)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Performance mode", style = MaterialTheme.typography.titleMedium)
                Text("Choose how Linux sessions balance responsiveness, resource use and battery.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PerformanceMode.entries.forEach { option ->
                        FilterChip(selected = mode == option, onClick = { mode = option; LauncherPreferences.setMode(context, option) }, label = { Text(option.label, fontSize = 10.sp) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic performance adaptation", style = MaterialTheme.typography.bodyMedium)
                        Text("Reduce Linux process priority and desktop resolution when RAM is low or the phone is hot.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = adaptive, onCheckedChange = { adaptive = it; LauncherPreferences.setAdaptive(context, it) })
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Advanced resource controls", style = MaterialTheme.typography.titleMedium)
                Text("These are best-effort controls for a rootless Android app, not hard CPU or RAM quotas. Android still decides system scheduling and memory reclaim.", style = MaterialTheme.typography.bodySmall)
                Text("Linux process scheduling", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Low", 2 to "Normal", 3 to "High").forEach { (value, label) ->
                        FilterChip(selected = cpuLevel == value, onClick = { cpuLevel = value; LauncherPreferences.setCpuLevel(context, value) }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
                Text("Free-RAM reserve before automatic throttling: ${ramReserve} MB", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(384, 512, 768, 1024).forEach { value ->
                        FilterChip(selected = ramReserve == value, onClick = { ramReserve = value; LauncherPreferences.setRamReserveMb(context, value) }, label = { Text("${value}M", fontSize = 10.sp) })
                    }
                }
                Text("Desktop resolution", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("AUTO", "800x600", "960x540", "1280x720").forEach { value ->
                        FilterChip(selected = desktopResolution == value, onClick = { desktopResolution = value; LauncherPreferences.setDesktopResolution(context, value) }, label = { Text(value, fontSize = 9.sp) })
                    }
                }
                Text("Resolution and scheduling settings apply when the next Linux process or desktop session starts. The RAM value is an adaptation threshold, not a memory cap.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium)
                Text("Download progress, completed installs, update events and errors appear in Android notifications when notification permission is enabled.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { LauncherNotifications.finished(context, "Linux Launcher", "Notifications are working.") }) { Text("Send test notification") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Installed Linux systems", style = MaterialTheme.typography.titleMedium)
                Text("Each installation has its own root filesystem and can be opened or removed independently.", style = MaterialTheme.typography.bodySmall)
                if (systems.isEmpty()) Text("No installed Linux systems found yet.", style = MaterialTheme.typography.bodySmall)
                systems.forEach { id ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(id, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onOpenDistro(id) }) { Text("Open") }
                        TextButton(onClick = { removeTarget = id }) { Text("Remove") }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            status = "Checking apt updates for $id…"
                            try { val count = LinuxUpdateChecker.check(context, id); status = if (count == 0) "$id is up to date." else "$count updates available for $id. Check Android notifications." }
                            catch (t: Throwable) { status = "Update check failed: ${t.message ?: "unknown error"}"; LauncherErrorHistory.record(context, "Linux update check", t.stackTraceToString()); LauncherNotifications.error(context, "Linux update check failed", status) }
                        }
                    }) { Text("Check package updates") }
                }
                OutlinedButton(onClick = { systems = LinuxSystemRegistry.list(context) }) { Text("Refresh list") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("External files", style = MaterialTheme.typography.titleMedium)
                Text("Use Android's file picker to copy files into a Linux system's /root/shared folder, then export files back out. Android shared storage is not directly executed or mounted because it may be no-exec and scoped-storage protected.", style = MaterialTheme.typography.bodySmall)
                if (systems.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        systems.forEach { id -> FilterChip(selected = targetDistro == id, onClick = { targetDistro = id }, label = { Text(id, fontSize = 10.sp) }) }
                    }
                    Button(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text("Import file into Linux") }
                    Text("Files in /root/shared", style = MaterialTheme.typography.labelMedium)
                    if (sharedFiles.isEmpty()) Text("No shared files yet.", style = MaterialTheme.typography.bodySmall)
                    sharedFiles.forEach { file ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(file.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { exportTarget = file; exportPicker.launch(file.name) }) { Text("Export") }
                        }
                    }
                } else Text("Install a Linux system to enable file transfer.", style = MaterialTheme.typography.bodySmall)
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Error history & diagnostics", style = MaterialTheme.typography.titleMedium)
                Text("${errors.size} recent recorded errors", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Linux Launcher diagnostics", LauncherErrorHistory.diagnostics(context)))
                        status = "Diagnostics copied to clipboard."
                    }) { Text("Copy diagnostics") }
                    OutlinedButton(onClick = {
                        val body = errors.joinToString("\n\n") { "${DateFormat.getDateTimeInstance().format(Date(it.time))} · ${it.category}\n${it.details}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Linux Launcher errors", body.ifBlank { "No recorded errors." }))
                        status = "Error history copied."
                    }) { Text("Copy errors") }
                }
                errors.take(8).forEach { item ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(9.dp)) {
                            Text("${item.category} · ${DateFormat.getDateTimeInstance().format(Date(item.time))}", style = MaterialTheme.typography.labelMedium)
                            Text(item.details, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { errors = LauncherErrorHistory.all(context) }) { Text("Refresh") }
                    TextButton(onClick = { LauncherErrorHistory.clear(context); errors = emptyList() }) { Text("Clear history") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Advanced Linux controls", style = MaterialTheme.typography.titleMedium)
                Text("Rootless PRoot sessions share Android's kernel. Android controls access to external storage, CPU scheduling and thermal limits; this app does not overclock or bypass those restrictions.", style = MaterialTheme.typography.bodySmall)
                Text("Linux Launcher · Advanced controls", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    removeTarget?.let { id ->
        AlertDialog(onDismissRequest = { removeTarget = null }, title = { Text("Remove $id?") },
            text = { Text("This permanently deletes this Linux installation and its files. Back it up first if you need it.") },
            confirmButton = { TextButton(onClick = {
                removeTarget = null
                scope.launch { val ok = withContext(Dispatchers.IO) { LinuxSystemRegistry.remove(context, id) }; systems = LinuxSystemRegistry.list(context); status = if (ok) "$id removed." else "Could not remove $id." }
            }) { Text("Delete Linux system") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } })
    }
}
