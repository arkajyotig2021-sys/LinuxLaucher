package app.nova.desktop.runtime

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.nova.desktop.graphics.GraphicsCapabilitiesProbe

@Composable
fun RuntimeAppsScreen(openBuiltIn: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = remember(context) { ExternalAppLauncher(context) }
    val apps = remember(context) { runCatching { launcher.installedLaunchableApps() }.getOrDefault(emptyList()) }
    val windowsManager = remember(context) { WindowsRuntimeManager(context) }
    val windows = windowsManager.status()
    val scope = rememberCoroutineScope()
    var selectedInstaller by remember { mutableStateOf<java.io.File?>(null) }
    var installerStatus by remember { mutableStateOf("") }
    val installerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { windowsManager.stageInstaller(uri) } }
                .onSuccess { selectedInstaller = it; installerStatus = "Staged: ${it.name} · SHA-256 ${withContext(Dispatchers.IO) { windowsManager.installerSha256(it) }}" }
                .onFailure { installerStatus = it.message ?: "Could not stage installer." }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { LinuxRuntimeCard(onInstall = { openBuiltIn("Linux Installer") }, onOpenTerminal = { openBuiltIn("Linux Terminal") }) }
        item {
            Text("NOVA workspace", style = MaterialTheme.typography.titleLarge)
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text("Windows compatibility", style = MaterialTheme.typography.titleMedium)
                    Text(windows.detail, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { installerPicker.launch(arrayOf("application/vnd.microsoft.portable-executable", "application/x-msdownload", "application/octet-stream")) }) { Text("Select EXE/MSI installer") }
                    selectedInstaller?.let { staged ->
                        Text("Selected: ${staged.name}", style = MaterialTheme.typography.bodySmall)
                        if (windows.state == WindowsBackendState.READY) {
                            Button(onClick = {
                                scope.launch {
                                    installerStatus = "Running Windows installer…"
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val process = windowsManager.install(staged)
                                            val output = process.inputStream.bufferedReader().use { it.readText() }
                                            process.waitFor() to output
                                        }
                                    }.onSuccess { (code, output) -> installerStatus = "Installer exit code: $code\\n" + output.take(1500) }
                                     .onFailure { installerStatus = it.message ?: "Windows installer could not start." }
                                }
                            }) { Text("Install selected package") }
                        }
                    }
                    if (installerStatus.isNotBlank()) Text(installerStatus, style = MaterialTheme.typography.labelSmall)

                }
            }
        }
        item { Text("NOVA tools", style = MaterialTheme.typography.titleMedium) }
        item {
            Card(onClick = { openBuiltIn("Notes") }) {
                ListItem(headlineContent = { Text("NOVA Notes") }, supportingContent = { Text("Built-in writing window") },
                    leadingContent = { Icon(Icons.Default.Apps, null) })
            }
        }
        item {
            Card(onClick = { openBuiltIn("System Monitor") }) {
                ListItem(headlineContent = { Text("System Monitor") }, supportingContent = { Text("App memory snapshot") },
                    leadingContent = { Icon(Icons.Default.Apps, null) })
            }
        }
        item { Text("Installed Android apps", style = MaterialTheme.typography.titleMedium) }
        if (apps.isEmpty()) {
            item { Text("No launchable apps were returned by Android package visibility.") }
        } else {
            items(apps, key = { it.packageName }) { app ->
                Card {
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { runCatching { launcher.launch(app.packageName) } }) { Text("Open") }
                                TextButton(onClick = { runCatching { launcher.launchInSystemWindow(app.packageName) } }) { Text("Window") }
                            }
                        }
                    )
                }
            }
        }
        item {
            Text("Window asks Android to open an app in a system-managed freeform window. The device firmware may ignore this request. NOVA cannot embed arbitrary third-party Activities inside its own windows.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun GraphicsInfoCard() {
    val context = LocalContext.current
    val caps = remember(context) { GraphicsCapabilitiesProbe.inspect(context) }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Graphics capability", style = MaterialTheme.typography.titleMedium)
            Text("OpenGL ES: " + caps.openGlesVersion)
            Text("GPU renderer: " + caps.renderer)
            Text("GPU vendor: " + caps.vendor)
            Text("Driver: " + caps.driverVersion)
            Text("Mali GPU detected: " + if (caps.isMali) "yes" else "no")
            Text("Guest GPU translation: " + if (caps.guestTranslationAvailable) "available" else "not implemented")
            Text("Vulkan hardware feature: " + if (caps.vulkanSupported) "available" else "not reported")
            Text("Supported ABI: " + caps.supportedAbis.joinToString())
            Text(caps.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}
