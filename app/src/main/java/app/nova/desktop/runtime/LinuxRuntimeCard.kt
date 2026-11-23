package app.nova.desktop.runtime

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LinuxRuntimeCard(onInstall: () -> Unit, onOpenTerminal: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { LinuxRuntimeManager(context) }
    var status by remember { mutableStateOf(manager.status()) }
    LaunchedEffect(manager) {
        while (true) {
            status = manager.status()
            delay(2500)
        }
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Linux environment", style = MaterialTheme.typography.titleMedium)
            Text("Alpine Linux · ARM64 · PRoot", style = MaterialTheme.typography.bodyMedium)
            Text(status.detail, style = MaterialTheme.typography.bodySmall)
            when (status.state) {
                LinuxInstallState.NOT_INSTALLED -> Button(onClick = onInstall) { Text("Install Alpine Linux") }
                LinuxInstallState.READY -> Button(onClick = onOpenTerminal) { Text("Open Linux terminal") }
                LinuxInstallState.ENGINE_MISSING -> Text("Rebuild NOVA with the bundled Linux engine.", color = MaterialTheme.colorScheme.error)
                LinuxInstallState.UNSUPPORTED -> Text("This device ABI is not supported by the current Linux engine.", color = MaterialTheme.colorScheme.error)
                LinuxInstallState.FAILED -> Text("Linux runtime reported an error.", color = MaterialTheme.colorScheme.error)
            }
            Text("The installer uses the PRoot package engine and verified distribution metadata. This is a real command-line Linux environment; graphical Linux desktops still need a display server and window bridge.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
