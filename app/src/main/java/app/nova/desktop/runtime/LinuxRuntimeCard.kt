package app.nova.desktop.runtime

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LinuxRuntimeCard() {
    val context = LocalContext.current
    val manager = remember(context) { LinuxRuntimeManager(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(manager.status()) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }

    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val progress = withContext(Dispatchers.IO) { manager.inspect(id) }
            if (progress.state == VerifiedDownloadState.VERIFIED || progress.state == VerifiedDownloadState.FAILED || progress.state == VerifiedDownloadState.MISSING) break
            if (progress.state == VerifiedDownloadState.VERIFYING) {
                busy = true
                status = LinuxInstallStatus(LinuxInstallState.EXTRACTING, "Verifying and extracting Alpine rootfs…", id, 100)
                runCatching { withContext(Dispatchers.IO) { manager.finishInstall(id) } }
                    .onSuccess { status = it; output = it.detail; downloadId = null }
                    .onFailure { status = LinuxInstallStatus(LinuxInstallState.FAILED, it.message ?: "Installation failed."); output = status.detail; downloadId = null }
                busy = false
                break
            }
            status = LinuxInstallStatus(LinuxInstallState.DOWNLOADING, progress.detail.ifBlank { "Downloading Alpine Linux…" }, id, progress.progressPercent)
            delay(1000)
        }
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Linux environment", style = MaterialTheme.typography.titleMedium)
            Text("Alpine Linux 3.24.4 · ARM64", style = MaterialTheme.typography.bodyMedium)
            Text(status.detail, style = MaterialTheme.typography.bodySmall)
            if (status.state == LinuxInstallState.DOWNLOADING) {
                LinearProgressIndicator(progress = { status.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${status.progress}%")
            }
            if (status.state == LinuxInstallState.NOT_INSTALLED || status.state == LinuxInstallState.FAILED) {
                Button(enabled = !busy && downloadId == null, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { manager.beginInstall() } }
                            .onSuccess { downloadId = it; status = LinuxInstallStatus(LinuxInstallState.DOWNLOADING, "Download queued.", it) }
                            .onFailure { status = LinuxInstallStatus(LinuxInstallState.FAILED, it.message ?: "Could not start download.") }
                        busy = false
                    }
                }) { Text(if (busy) "Preparing…" else "Install Alpine rootfs") }
            }
            if (status.state == LinuxInstallState.ENGINE_MISSING) {
                Text("The root filesystem can be installed, but this build still needs its bundled PRoot executable before Linux commands can run.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (status.state == LinuxInstallState.READY) {
                Text("Linux command backend is ready.", color = MaterialTheme.colorScheme.primary)
            }
            if (output.isNotBlank()) Text(output, style = MaterialTheme.typography.bodySmall)
            Text("This installs a minimal command-line root filesystem. A graphical desktop needs a display server and input bridge, which are not bundled yet.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
