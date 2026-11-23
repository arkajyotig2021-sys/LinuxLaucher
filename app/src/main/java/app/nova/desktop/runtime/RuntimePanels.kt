package app.nova.desktop.runtime

import androidx.compose.foundation.layout.*
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
    val windows = remember { UnavailableGuestRuntime(RuntimeKind.WINDOWS).status(context) }
    val linux = remember(context) { LinuxRuntimeManager(context) }.status()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { LinuxRuntimeCard() }
        item {
            Text("NOVA workspace", style = MaterialTheme.typography.titleLarge)
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text("Windows compatibility", style = MaterialTheme.typography.titleMedium)
                    Text(windows.detail, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Linux desktop runtime", style = MaterialTheme.typography.titleMedium)
                    Text(linux.detail, style = MaterialTheme.typography.bodySmall)
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
            Text("Vulkan hardware feature: " + if (caps.vulkanSupported) "available" else "not reported")
            Text("Supported ABI: " + caps.supportedAbis.joinToString())
            Text(caps.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}
