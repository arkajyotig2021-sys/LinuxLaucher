package app.nova.desktop.runtime

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class LinuxTerminalMode { INSTALL, LOGIN }

@Composable
fun LinuxTerminalScreen(mode: LinuxTerminalMode) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember(context) { LinuxRuntimeManager(context) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<id.or.oo.pr.engine.ProotLauncher.Session?>(null) }
    var output by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (mode == LinuxTerminalMode.INSTALL) "Starting Alpine installer…" else "Starting Linux shell…") }
    val scroll = rememberScrollState()

    LaunchedEffect(mode) {
        val started = runCatching {
            withContext(Dispatchers.IO) {
                if (mode == LinuxTerminalMode.INSTALL) manager.startInstall() else manager.startLogin()
            }
        }
        if (started.isFailure) {
            status = started.exceptionOrNull()?.message ?: "Could not start Linux."
            return@LaunchedEffect
        }
        val active = started.getOrThrow()
        session = active
        status = if (mode == LinuxTerminalMode.INSTALL) "Installing Alpine Linux…" else "Linux shell running."
        val buffer = ByteArray(8192)
        while (coroutineContext.isActive) {
            val count = withContext(Dispatchers.IO) { active.read(buffer, 0, buffer.size) }
            if (count > 0) {
                val raw = String(buffer, 0, count, Charsets.UTF_8)
                val clean = Regex("\u001B\\[[0-?]*[ -/]*[@-~]").replace(raw, "")
                output = (output + clean).takeLast(30000)
            } else if (active.exitCode != null || count < 0) {
                status = "Process finished · exit ${active.exitCode ?: "unknown"}"
                break
            } else {
                delay(25)
            }
        }
    }

    DisposableEffect(session) {
        onDispose { session?.close() }
    }

    LaunchedEffect(output) { scroll.scrollTo(scroll.maxValue) }

    fun send(text: String) {
        val active = session ?: return
        scope.launch(Dispatchers.IO) { active.write(text.toByteArray(Charsets.UTF_8)) }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.weight(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Text(output.ifBlank { "Linux terminal output will appear here." },
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(10.dp),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Enter a Linux command") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input + "\n"); input = "" })
            )
            Button(onClick = { send(input + "\n"); input = "" }, enabled = session != null) { Text("Send") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { send("\u0003") }, enabled = session != null) { Text("Ctrl+C") }
            OutlinedButton(onClick = { send("\t") }, enabled = session != null) { Text("Tab") }
            OutlinedButton(onClick = { send("\u001B[A") }, enabled = session != null) { Text("↑") }
            OutlinedButton(onClick = { send("\u0004") }, enabled = session != null) { Text("Ctrl+D") }
        }
    }
}
