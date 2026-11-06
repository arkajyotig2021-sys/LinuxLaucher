package com.mindflux.linuxlauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mindflux.linuxlauncher.data.Distro
import com.mindflux.linuxlauncher.data.DistroCatalog

private enum class Page(val label: String) {
    DASHBOARD("Dashboard"),
    DISTROS("Distributions"),
    INSTALLED("Installed"),
    DOWNLOADS("Downloads"),
    PERFORMANCE("Performance"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherApp() {
    var page by remember { mutableStateOf(Page.DASHBOARD) }
    var search by remember { mutableStateOf("") }
    var selectedDistro by remember { mutableStateOf<Distro?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Linux Launcher", fontWeight = FontWeight.Bold)
                        Text("Android Linux environment manager", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { page = Page.SETTINGS }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == Page.DASHBOARD,
                    onClick = { page = Page.DASHBOARD },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = page == Page.DISTROS,
                    onClick = { page = Page.DISTROS },
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    label = { Text("Distros") }
                )
                NavigationBarItem(
                    selected = page == Page.DOWNLOADS,
                    onClick = { page = Page.DOWNLOADS },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    label = { Text("Downloads") }
                )
                NavigationBarItem(
                    selected = page == Page.PERFORMANCE,
                    onClick = { page = Page.PERFORMANCE },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    label = { Text("Performance") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                Page.DASHBOARD -> DashboardPage(
                    onBrowse = { page = Page.DISTROS },
                    onSettings = { page = Page.SETTINGS }
                )
                Page.DISTROS -> DistroPage(
                    query = search,
                    onQueryChange = { search = it },
                    onSelect = { selectedDistro = it }
                )
                Page.INSTALLED -> EmptyFeaturePage("Installed distributions", "No Linux environments are installed yet.")
                Page.DOWNLOADS -> EmptyFeaturePage("Downloads", "The download queue will appear here when the download engine is implemented.")
                Page.PERFORMANCE -> EmptyFeaturePage("Performance monitor", "Live CPU, memory, temperature and per-environment metrics require the runtime integration phase.")
                Page.SETTINGS -> EmptyFeaturePage("Settings", "Application preferences and advanced runtime settings will be added in later phases.")
            }
        }
    }

    selectedDistro?.let { distro ->
        AlertDialog(
            onDismissRequest = { selectedDistro = null },
            title = { Text(distro.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(distro.description)
                    Text("Family: ${distro.family}")
                    Text("Desktop options: ${distro.desktopHint}")
                    Text("Architecture: ${distro.architecture}")
                    Text(
                        "Catalogue entry only. Installation is not enabled until a compatible ARM64 root filesystem and runtime backend are integrated.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDistro = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun DashboardPage(onBrowse: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Your Linux workspace", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Explore distributions and prepare your Android device for a Linux desktop environment.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Development build · Phase 1", style = MaterialTheme.typography.labelLarge)
                Text("The app foundation is ready for runtime development.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("This build does not install or boot Linux yet. The runtime will be developed and tested separately.")
                Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Explore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Browse distributions")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile("Installed", "0", Modifier.weight(1f))
            SummaryTile("Running", "0", Modifier.weight(1f))
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Target device: OPPO A6X 5G")
                Text("Physical RAM: 4 GB · Internal storage: 128 GB")
                Text("Recommended first desktop: XFCE or LXQt")
            }
        }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Application settings")
        }
    }
}

@Composable
private fun SummaryTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DistroPage(
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (Distro) -> Unit
) {
    val results = remember(query) { DistroCatalog.search(query) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Linux distributions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search distributions") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            }
        )
        Text("${results.size} distributions in catalogue", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 10.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(results, key = { it.id }) { distro ->
                Card(onClick = { onSelect(distro) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(distro.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(distro.family, style = MaterialTheme.typography.bodySmall)
                            Text(distro.desktopHint, style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Details")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFeaturePage(title: String, description: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}
