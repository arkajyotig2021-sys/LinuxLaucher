package com.mindflux.linuxlauncher.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mindflux.linuxlauncher.data.Distro
import com.mindflux.linuxlauncher.runtime.LinuxRuntimeSession
import com.mindflux.linuxlauncher.runtime.OciRootfsInstaller
import com.mindflux.linuxlauncher.runtime.RuntimeBinaryLocator
import com.mindflux.linuxlauncher.runtime.RuntimeCommandBuilder
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LinuxInstallDialog(
    distro: Distro,
    onDismiss: () -> Unit,
    onInstalled: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var image by remember(distro.id) { mutableStateOf(distro.imageRef) }
    val root = File(context.filesDir, "linux-containers/" + distro.id + "/rootfs")
    var running by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf(root.resolve("bin").exists() || root.resolve("usr/bin").exists()) }
    var message by remember { mutableStateOf(if (complete) "This Linux system is already installed." else "Ready to install.") }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    val installer = remember(context) { OciRootfsInstaller(context.filesDir) }
    var interrupted by remember(distro.id) { mutableStateOf(installer.hasPartialDownloads(distro.id)) }
    var installJob by remember { mutableStateOf<Job?>(null) }
    var discardPartialOnStop by remember { mutableStateOf(false) }
    val startInstall: (Boolean) -> Unit = { resume ->
        running = true
        error = null
        discardPartialOnStop = false
        installJob = scope.launch {
            try {
                installer.install(image.trim(), distro.id, resume) { p ->
                    message = p.message
                    if (p.total > 0) progress = (p.downloaded.toFloat() / p.total).coerceIn(0f, 1f)
                }
                complete = true
                interrupted = false
                message = "Installation complete."
            } catch (cancelled: CancellationException) {
                error = null
                message = if (discardPartialOnStop) "Cancelled. Partial downloads removed." else "Paused. Your downloaded data is saved; choose Resume to continue."
            } catch (t: Throwable) {
                error = t.message ?: "Installation failed."
                message = "Installation did not complete."
            } finally {
                if (discardPartialOnStop) installer.clearPartialDownloads(distro.id)
                interrupted = installer.hasPartialDownloads(distro.id)
                running = false
                installJob = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(if (complete) distro.name + " is installed" else "Install " + distro.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(distro.description)
                if (!complete) {
                    OutlinedTextField(
                        value = image,
                        onValueChange = { image = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OCI image reference") },
                        placeholder = { Text("ubuntu:24.04 or ghcr.io/owner/image:tag") },
                        singleLine = true
                    )
                }
                Text(
                    when {
                        complete -> "The root filesystem is stored in this app's private storage."
                        image.isNotBlank() -> "Public OCI image: " + image + "\nThe installer verifies every downloaded layer before extraction."
                        else -> "Enter a public OCI Linux image reference above. Example: alpine:3.22 or ghcr.io/owner/image:tag."
                    }
                )
                if (interrupted && !running) {
                    Text("An interrupted layer download was saved. Resume it, or restart and discard the partial files.")
                }
                if (running) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text(message)
                    }
                } else Text(message)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Linux runs in a rootless PRoot environment and shares Android's kernel. It is not a full virtual machine.")
            }
        },
        confirmButton = {
            when {
                complete -> TextButton(onClick = { onInstalled(distro.id) }) { Text("Open terminal") }
                running -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { discardPartialOnStop = false; installJob?.cancel() }) { Text("Pause") }
                    TextButton(onClick = { discardPartialOnStop = true; installJob?.cancel() }) { Text("Cancel") }
                }
                image.isNotBlank() && !running -> {
                    if (interrupted) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { startInstall(true) }) { Text("Resume") }
                            TextButton(onClick = { startInstall(false) }) { Text("Restart") }
                        }
                    } else {
                        TextButton(onClick = { startInstall(true) }) { Text("Install Linux") }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) { Text(if (running) "Installing…" else "Close") }
        }
    )
}

@Composable
fun LinuxTerminalDialog(distroId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val output = remember { mutableStateListOf<String>() }
    var command by remember { mutableStateOf("") }
    var process by remember { mutableStateOf<Process?>(null) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(distroId) {
        try {
            val binary = RuntimeBinaryLocator(context).prootExecutable()
                ?: error("The PRoot engine is not included in this build.")
            val root = RuntimeCommandBuilder.rootfsDirectory(context.filesDir, distroId)
            val p = withContext(Dispatchers.IO) { LinuxRuntimeSession(binary, root).startShell() }
            process = p
            ready = true
            output.add("Linux shell started. Try: uname -a")
            withContext(Dispatchers.IO) {
                val reader = p.inputStream.bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    withContext(Dispatchers.Main) { output.add(line) }
                }
            }
        } catch (t: Throwable) {
            output.add("Launch error: " + (t.message ?: "Unknown error"))
        }
    }

    DisposableEffect(Unit) {
        onDispose { process?.destroy() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF101923)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(distroId.uppercase() + "  /  LINUX TERMINAL", color = Color(0xFF69E6B7))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp)
                        .background(Color(0xFF080D13), RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(output) { line -> Text(line, color = Color(0xFFE1E8EF), style = MaterialTheme.typography.bodySmall) }
                }
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Linux command") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = ready && command.isNotBlank() && process?.isAlive == true,
                        onClick = {
                            val p = process ?: return@Button
                            try {
                                p.outputStream.write((command + "\n").toByteArray())
                                p.outputStream.flush()
                                output.add("$ " + command)
                                command = ""
                            } catch (t: Throwable) {
                                output.add("Input error: " + (t.message ?: "unknown"))
                            }
                        }
                    ) { Text("Run") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close terminal") }
                }
            }
        }
    }
}
