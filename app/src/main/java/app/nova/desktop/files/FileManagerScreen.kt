package app.nova.desktop.files

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import java.util.Locale

private enum class FileDialog { NONE, NEW_FOLDER, NEW_FILE, RENAME, DELETE, DETAILS }

@Composable
fun FileManagerScreen(tree: Uri?, chooseFolder: () -> Unit) {
    val context = LocalContext.current
    val ops = remember(context) { SpatialFileOperations(context) }
    val root = remember(tree) { tree?.let { DocumentFile.fromTreeUri(context, it) } }
    var path by remember(tree) { mutableStateOf(listOfNotNull(root)) }
    val current = path.lastOrNull()
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Name") }
    var showHidden by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(FileDialog.NONE) }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<DocumentFile?>(null) }
    var status by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    var clipboard by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var clipboardIsCut by remember { mutableStateOf(false) }

    val allItems = remember(current, refresh, showHidden, sort) {
        runCatching { current?.let { ops.list(it) }.orEmpty() }.getOrDefault(emptyList())
            .filter { showHidden || !it.name.orEmpty().startsWith(".") }
            .let { list ->
                when (sort) {
                    "Size" -> list.sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenByDescending { it.length() })
                    "Modified" -> list.sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenByDescending { it.lastModified() })
                    else -> list.sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })
                }
            }
    }
    val visibleItems = remember(allItems, search) {
        if (search.isBlank()) allItems else allItems.filter { it.name.orEmpty().contains(search, ignoreCase = true) }
    }
    val selectedItems = allItems.filter { it.uri.toString() in selected }

    fun refreshList() { refresh++; selected.clear() }
    fun perform(label: String, action: () -> Unit) {
        val result = runCatching(action)
        status = result.fold({ label + " completed" }, { label + " failed: " + (it.message ?: "storage provider rejected the operation") })
        refresh++
    }
    fun openDocument(file: DocumentFile) {
        try {
            context.startActivity(Intent.createChooser(ops.openIntent(file), "Open with"))
        } catch (_: ActivityNotFoundException) {
            status = "No installed app can open this file."
        } catch (e: Exception) {
            status = "Could not open file: " + e.message
        }
    }
    fun navigate(file: DocumentFile) {
        path = path + file
        search = ""
        selected.clear()
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Spatial Files", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("User-approved storage · SAF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = chooseFolder) { Icon(Icons.Default.FolderOpen, "Choose storage folder") }
                    IconButton(onClick = { refreshList() }) { Icon(Icons.Default.Refresh, "Refresh") }
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search this folder") },
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") } }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (path.size > 1) { path = path.dropLast(1); selected.clear() } }, enabled = path.size > 1) {
                        Icon(Icons.Default.ArrowBack, "Parent folder")
                    }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        path.forEachIndexed { index, folder ->
                            if (index > 0) Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(
                                onClick = { path = path.take(index + 1); selected.clear() },
                                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
                            ) {
                                Text(folder.name ?: "Storage", maxLines = 1, fontSize = 11.sp,
                                    fontWeight = if (index == path.lastIndex) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }

        if (current == null) {
            Card {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Choose a folder to get started", fontWeight = FontWeight.Bold)
                    Text("NOVA only accesses storage you explicitly grant.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Button(onClick = chooseFolder) { Text("Choose folder") }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(visibleItems.size.toString() + " items", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected.isNotEmpty()) {
                    Text(selected.size.toString() + " selected", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { clipboard = selectedItems; clipboardIsCut = false; selected.clear(); status = "Copied to NOVA clipboard" }) {
                        Icon(Icons.Default.ContentCopy, "Copy")
                    }
                    IconButton(onClick = { clipboard = selectedItems; clipboardIsCut = true; selected.clear(); status = "Ready to move" }) {
                        Icon(Icons.Default.ContentCut, "Cut")
                    }
                    IconButton(onClick = { dialog = FileDialog.DELETE }) { Icon(Icons.Default.DeleteOutline, "Delete selected") }
                }
                IconButton(onClick = { showHidden = !showHidden }) {
                    Icon(if (showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle hidden files")
                }
                var sortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("Name", "Size", "Modified").forEach { option ->
                            DropdownMenuItem(text = { Text("Sort by " + option) }, onClick = { sort = option; sortMenu = false })
                        }
                    }
                }
                var addMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Create") }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text("New folder") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            onClick = { name = ""; dialog = FileDialog.NEW_FOLDER; addMenu = false })
                        DropdownMenuItem(text = { Text("New empty file") }, leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
                            onClick = { name = ""; dialog = FileDialog.NEW_FILE; addMenu = false })
                    }
                }
                if (clipboard.isNotEmpty()) {
                    IconButton(onClick = {
                        perform(if (clipboardIsCut) "Move" else "Paste") {
                            clipboard.toList().forEach { source ->
                                if (clipboardIsCut) ops.move(source, current) else ops.copy(source, current)
                            }
                            clipboard = emptyList()
                            clipboardIsCut = false
                        }
                    }) { Icon(Icons.Default.ContentPaste, "Paste here") }
                }
            }

            if (visibleItems.isEmpty()) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
                        Text(if (search.isBlank()) "This folder is empty" else "No matching files", fontWeight = FontWeight.SemiBold)
                        Text(if (search.isBlank()) "Create a folder or file using the + button." else "Try another search.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                    items(visibleItems, key = { it.uri.toString() }) { item ->
                        val isSelected = item.uri.toString() in selected
                        Card(
                            colors = CardDefaults.cardColors(containerColor =
                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (selected.isNotEmpty()) {
                                        if (isSelected) selected.remove(item.uri.toString()) else selected.add(item.uri.toString())
                                    } else if (item.isDirectory) navigate(item) else openDocument(item)
                                }.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selected.isNotEmpty()) {
                                    Checkbox(checked = isSelected, onCheckedChange = {
                                        if (it) selected.add(item.uri.toString()) else selected.remove(item.uri.toString())
                                    })
                                } else {
                                    Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name ?: "Unnamed", maxLines = 1, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    val detail = if (item.isDirectory) {
                                        "Folder · " + runCatching { item.listFiles().size }.getOrDefault(0).toString() + " items"
                                    } else {
                                        formatBytes(item.length()) + " · " + item.lastModified().let {
                                            if (it > 0) java.text.DateFormat.getDateInstance().format(it) else "Date unavailable"
                                        }
                                    }
                                    Text(detail, maxLines = 1, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More actions") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) }, onClick = {
                                            menu = false
                                            if (item.isDirectory) navigate(item) else openDocument(item)
                                        })
                                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }, onClick = {
                                            target = item; name = item.name.orEmpty(); dialog = FileDialog.RENAME; menu = false
                                        })
                                        DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                                            clipboard = listOf(item); clipboardIsCut = false; status = "Copied to NOVA clipboard"; menu = false
                                        })
                                        DropdownMenuItem(text = { Text("Cut") }, leadingIcon = { Icon(Icons.Default.ContentCut, null) }, onClick = {
                                            clipboard = listOf(item); clipboardIsCut = true; status = "Ready to move"; menu = false
                                        })
                                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = {
                                            menu = false
                                            runCatching { context.startActivity(Intent.createChooser(ops.shareIntent(item), "Share file")) }
                                                .onFailure { status = "Sharing failed: " + it.message }
                                        })
                                        DropdownMenuItem(text = { Text("Details") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = {
                                            target = item; dialog = FileDialog.DETAILS; menu = false
                                        })
                                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = {
                                            selected.clear(); selected.add(item.uri.toString()); dialog = FileDialog.DELETE; menu = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }

    when (dialog) {
        FileDialog.NEW_FOLDER, FileDialog.NEW_FILE, FileDialog.RENAME -> {
            val title = when (dialog) {
                FileDialog.NEW_FOLDER -> "Create folder"
                FileDialog.NEW_FILE -> "Create empty file"
                else -> "Rename item"
            }
            AlertDialog(
                onDismissRequest = { dialog = FileDialog.NONE },
                title = { Text(title) },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        val operation = dialog
                        perform(title) {
                            when (operation) {
                                FileDialog.NEW_FOLDER -> ops.createFolder(current ?: error("No folder selected"), name)
                                FileDialog.NEW_FILE -> ops.createFile(current ?: error("No folder selected"), name)
                                FileDialog.RENAME -> if (!ops.rename(target ?: error("No item selected"), name)) error("Storage provider refused rename")
                                else -> Unit
                            }
                        }
                        dialog = FileDialog.NONE; name = ""; target = null
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { dialog = FileDialog.NONE }) { Text("Cancel") } }
            )
        }
        FileDialog.DELETE -> {
            AlertDialog(
                onDismissRequest = { dialog = FileDialog.NONE },
                title = { Text("Delete " + selected.size + " item(s)?") },
                text = { Text("The selected storage provider will be asked to delete these items. This may not be reversible.") },
                confirmButton = {
                    TextButton(onClick = {
                        val toDelete = selectedItems.toList()
                        perform("Delete") {
                            toDelete.forEach { if (!ops.delete(it)) error("Could not delete " + it.name) }
                        }
                        dialog = FileDialog.NONE
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { dialog = FileDialog.NONE }) { Text("Cancel") } }
            )
        }
        FileDialog.DETAILS -> {
            val file = target
            AlertDialog(
                onDismissRequest = { dialog = FileDialog.NONE },
                title = { Text(file?.name ?: "Details") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (file?.isDirectory == true) "Folder" else "File")
                        Text("Size: " + (file?.let { if (it.isDirectory) "Not provided by provider" else formatBytes(it.length()) } ?: "Unknown"))
                        Text("Modified: " + (file?.let {
                            it.lastModified().takeIf { time -> time > 0 }?.let { time ->
                                java.text.DateFormat.getDateTimeInstance().format(time)
                            } ?: "Unavailable"
                        } ?: "Unknown"))
                        Text("MIME: " + (file?.let { context.contentResolver.getType(it.uri) } ?: "Unavailable"))
                        Text("Location: " + (file?.uri?.toString() ?: "Unknown"), fontSize = 10.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = FileDialog.NONE }) { Text("Close") } }
            )
        }
        FileDialog.NONE -> Unit
    }
}

private fun formatBytes(value: Long): String = when {
    value < 1024 -> value.toString() + " B"
    value < 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f KB", value / 1024.0)
    value < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", value / (1024.0 * 1024))
    else -> String.format(Locale.getDefault(), "%.2f GB", value / (1024.0 * 1024 * 1024))
}
