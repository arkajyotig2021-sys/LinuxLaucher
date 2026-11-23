package app.nova.desktop

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.app.ActivityManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import app.nova.desktop.files.FileManagerScreen
import app.nova.desktop.runtime.GraphicsInfoCard
import app.nova.desktop.runtime.RuntimeAppsScreen
import java.util.Locale

private val NovaBlue = Color(0xFF596DE8)
private val Quiet = Color(0xFF788196)

@Composable
fun NovaApp(tree: Uri?, chooseFolder: () -> Unit) {
 val context = LocalContext.current
 val preferences = remember(context) { context.getSharedPreferences("nova", Context.MODE_PRIVATE) }
 var page by remember { mutableStateOf("Home") }
 var dark by remember { mutableStateOf(preferences.getBoolean("dark_appearance", false)) }
 var profile by remember { mutableStateOf(preferences.getString("performance_profile", "Smooth") ?: "Smooth") }
 LaunchedEffect(dark, profile) {
  preferences.edit()
   .putBoolean("dark_appearance", dark)
   .putString("performance_profile", profile)
   .apply()
 }
 var window by remember { mutableStateOf<String?>(null) }
 val colors = if (dark) darkColorScheme(primary = Color(0xFF9AA8FF)) else lightColorScheme(primary = NovaBlue)
 MaterialTheme(colorScheme = colors) {
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
   Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) {
      Text("NOVA", fontSize = 12.sp, fontWeight = FontWeight.Black, color = NovaBlue)
      Text(page, fontSize = 27.sp, fontWeight = FontWeight.Bold)
     }
     IconButton(onClick = { dark = !dark }) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Appearance") }
    }
    when (page) {
     "Home" -> HomeScreen(onFiles = { page = "Files" }, onOpen = { window = it })
     "Files" -> FileManagerScreen(tree, chooseFolder)
     "Apps" -> RuntimeAppsScreen { window = it }
     "Performance" -> PerformanceScreen(profile) { profile = it }
     else -> SettingsScreen(dark, { dark = it }, profile, { profile = it })
    }
    Spacer(Modifier.weight(1f))
    NavigationBar {
     listOf("Home", "Files", "Apps", "Performance", "Settings").forEach { name ->
      NavigationBarItem(selected = page == name, onClick = { page = name }, icon = {
       Icon(when(name) {
        "Home" -> Icons.Default.Home
        "Files" -> Icons.Default.Folder
        "Apps" -> Icons.Default.Apps
        "Performance" -> Icons.Default.Speed
        else -> Icons.Default.Settings
       }, contentDescription = name)
      }, label = { Text(name, fontSize = 9.sp) })
     }
    }
   }
   window?.let { NovaWindow(it, onClose = { window = null }) }
  }
 }
}

@Composable
private fun HomeScreen(onFiles: () -> Unit, onOpen: (String) -> Unit) {
 val free = remember { runCatching { StatFs(Environment.getDataDirectory().path).availableBytes / (1024L * 1024 * 1024) }.getOrDefault(0L) }
 val heap = Runtime.getRuntime().maxMemory() / (1024 * 1024)
 LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
  item {
   Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18233C)), shape = RoundedCornerShape(22.dp)) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
     Text("A calmer way to compute.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
     Text("A touch-first workspace for your Android device.", color = Color(0xFFD0D8EB), fontSize = 12.sp)
     Spacer(Modifier.height(14.dp))
     Button(onClick = onFiles) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Explore files") }
    }
   }
  }
  item { Text("Device snapshot", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
  item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
   Metric("Android", Build.VERSION.RELEASE ?: "Unknown", Modifier.weight(1f))
   Metric("Free storage", free.toString() + " GB", Modifier.weight(1f))
  } }
  item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
   Metric("App heap cap", heap.toString() + " MB", Modifier.weight(1f))
   Metric("CPU ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown", Modifier.weight(1f))
  } }
  item { Text("Workspace tools", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
  item { ToolCard("NOVA Notes", "A simple writing window", Icons.Default.EditNote) { onOpen("Notes") } }
  item { ToolCard("System Monitor", "App memory and processor snapshot", Icons.Default.Memory) { onOpen("System Monitor") } }
  item {
   Card(shape = RoundedCornerShape(17.dp)) {
    Column(Modifier.padding(15.dp)) {
     Text("Compatibility engines", fontWeight = FontWeight.Bold)
     Text("Windows: native runtime not bundled", color = Quiet, fontSize = 12.sp)
     Text("Linux: userspace not bundled", color = Quiet, fontSize = 12.sp)
     Text("This build does not claim Windows or Linux binary execution.", color = Quiet, fontSize = 11.sp)
    }
   }
  }
 }
}

@Composable
private fun FilesScreen(tree: Uri?, chooseFolder: () -> Unit) {
 val context = LocalContext.current
 var root by remember(tree) { mutableStateOf(tree?.let { DocumentFile.fromTreeUri(context, it) }) }
 var current by remember(tree) { mutableStateOf(root) }
 var refresh by remember { mutableIntStateOf(0) }
 var makeFolder by remember { mutableStateOf(false) }
 var folderName by remember { mutableStateOf("") }
 var status by remember { mutableStateOf("") }
 Column(Modifier.fillMaxSize()) {
  Card(shape = RoundedCornerShape(17.dp)) {
   Column(Modifier.padding(14.dp)) {
    Text("Spatial Files", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text("Browse only the shared folder you explicitly approve with Android.", color = Quiet, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     Button(onClick = chooseFolder) { Text(if (tree == null) "Choose folder" else "Change folder") }
     if (root != null) OutlinedButton(onClick = { current = root }) { Text("Root") }
    }
   }
  }
  Spacer(Modifier.height(10.dp))
  if (current != null) {
   Row(verticalAlignment = Alignment.CenterVertically) {
    Text(current?.name ?: "Selected folder", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
    IconButton(onClick = { makeFolder = true }) { Icon(Icons.Default.CreateNewFolder, "New folder") }
    IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh") }
   }
   val list = remember(current, refresh) { runCatching { current!!.listFiles().sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name ?: "" }) }.getOrDefault(emptyList()) }
   LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    items(list, key = { it.uri.toString() }) { item ->
     Card(shape = RoundedCornerShape(12.dp)) {
      Row(Modifier.fillMaxWidth().clickable {
       if (item.isDirectory) current = item else openFile(context, item)
      }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
       Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = NovaBlue)
       Spacer(Modifier.width(10.dp))
       Column(Modifier.weight(1f)) {
        Text(item.name ?: "Unnamed", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(if (item.isDirectory) "Folder" else readableSize(item.length()), fontSize = 10.sp, color = Quiet)
       }
       IconButton(onClick = {
        status = if (runCatching { item.delete() }.getOrDefault(false)) "Deleted" else "Android denied this operation"
        refresh++
       }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Quiet) }
      }
     }
    }
   }
   if (list.isEmpty()) Text("This folder is empty.", color = Quiet, modifier = Modifier.padding(14.dp))
  } else {
   Spacer(Modifier.height(12.dp))
   Text("Choose a folder to begin. NOVA will not request unrestricted storage access.", color = Quiet)
  }
  if (status.isNotEmpty()) Text(status, color = Quiet, fontSize = 11.sp)
 }
 if (makeFolder) {
  AlertDialog(onDismissRequest = { makeFolder = false }, title = { Text("Create folder") }, text = {
   OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Folder name") })
  }, confirmButton = {
   TextButton(onClick = {
    status = if (runCatching { current?.createDirectory(folderName.trim()) }.getOrNull() != null) "Folder created" else "Could not create folder"
    folderName = ""; makeFolder = false; refresh++
   }, enabled = folderName.isNotBlank()) { Text("Create") }
  }, dismissButton = { TextButton(onClick = { makeFolder = false }) { Text("Cancel") } })
 }
}

private fun openFile(context: Context, file: DocumentFile) {
 val intent = Intent(Intent.ACTION_VIEW).apply {
  setDataAndType(file.uri, context.contentResolver.getType(file.uri) ?: "*/*")
  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
 }
 try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { }
}
private fun readableSize(bytes: Long): String = if (bytes > 1024L * 1024) String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024)) else bytes.toString() + " bytes"

@Composable
private fun PerformanceScreen(profile: String, setProfile: (String) -> Unit) {
 val context = LocalContext.current
 val rt = Runtime.getRuntime()
 var appCpuPercent by remember { mutableFloatStateOf(0f) }
 var appPssMb by remember { mutableIntStateOf(0) }
 var availableRamMb by remember { mutableIntStateOf(0) }
 var thermal by remember { mutableStateOf("Not available") }
 LaunchedEffect(context) {
  var previousCpu = android.os.Process.getElapsedCpuTime()
  var previousWall = SystemClock.elapsedRealtime()
  while (true) {
   delay(2000)
   val currentCpu = android.os.Process.getElapsedCpuTime()
   val currentWall = SystemClock.elapsedRealtime()
   val cpuDelta = (currentCpu - previousCpu).coerceAtLeast(0L)
   val wallDelta = (currentWall - previousWall).coerceAtLeast(1L)
   appCpuPercent = ((cpuDelta.toFloat() / wallDelta.toFloat()) * 100f).coerceIn(0f, 100f)
   previousCpu = currentCpu
   previousWall = currentWall
   appPssMb = Debug.getPss() / 1024
   val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
   val memory = ActivityManager.MemoryInfo()
   manager.getMemoryInfo(memory)
   availableRamMb = (memory.availMem / (1024L * 1024L)).toInt()
   val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
   thermal = if (Build.VERSION.SDK_INT >= 29) {
    when (power.currentThermalStatus) {
     PowerManager.THERMAL_STATUS_NONE -> "Normal"
     PowerManager.THERMAL_STATUS_LIGHT -> "Light"
     PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
     PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
     PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
     PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
     PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
     else -> "Unknown"
    }
   } else "Android API below 29"
  }
 }
 val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
 Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Text("Live resource overview", fontSize = 18.sp, fontWeight = FontWeight.Bold)
  Metric("NOVA app CPU (approx.)", String.format(Locale.getDefault(), "%.1f%%", appCpuPercent), Modifier.fillMaxWidth())
  Metric("App memory (PSS)", "$appPssMb MB", Modifier.fillMaxWidth())
  Metric("App heap in use", used.toString() + " / " + (rt.maxMemory() / (1024 * 1024)) + " MB", Modifier.fillMaxWidth())
  Metric("System available RAM", "$availableRamMb MB", Modifier.fillMaxWidth())
  Metric("Thermal status", thermal, Modifier.fillMaxWidth())
  Metric("Available processors", rt.availableProcessors().toString(), Modifier.fillMaxWidth())
  GraphicsInfoCard()
  Text("Performance preference", fontWeight = FontWeight.SemiBold)
  listOf("Smooth", "Balanced", "Battery Saver").forEach {
   Card(Modifier.fillMaxWidth().clickable { setProfile(it) }) {
    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
     Text(it, modifier = Modifier.weight(1f))
     if (profile == it) Icon(Icons.Default.CheckCircle, null, tint = NovaBlue)
    }
   }
  }
  Text("Metrics refresh every 2 seconds. CPU is this app's process usage, not total device CPU. The profile is currently a preference only; Android does not expose general CPU/GPU clock control to ordinary apps.", color = Quiet, fontSize = 11.sp)
 } 
}

@Composable
private fun SettingsScreen(dark: Boolean, setDark: (Boolean) -> Unit, profile: String, setProfile: (String) -> Unit) {
 Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 18.sp)
  Row(verticalAlignment = Alignment.CenterVertically) {
   Text("Dark appearance", modifier = Modifier.weight(1f))
   Switch(checked = dark, onCheckedChange = setDark)
  }
  HorizontalDivider()
  Text("Performance profile: " + profile)
  listOf("Smooth", "Balanced", "Battery Saver").forEach {
   Row(verticalAlignment = Alignment.CenterVertically) {
    RadioButton(selected = profile == it, onClick = { setProfile(it) })
    Text(it)
   }
  }
  HorizontalDivider()
  Text("NOVA Preview 0.2.0", fontWeight = FontWeight.Bold)
  Text("Windows/Linux execution and guest GPU translation remain unavailable until their native backends are packaged.", color = Quiet, fontSize = 12.sp)
 }
}

@Composable
private fun NovaWindow(name: String, onClose: () -> Unit) {
 val context = LocalContext.current
 val preferences = remember(context) { context.getSharedPreferences("nova", Context.MODE_PRIVATE) }
 var note by remember { mutableStateOf(preferences.getString("notes_document", "") ?: "") }
 LaunchedEffect(note) {
  if (name == "Notes") preferences.edit().putString("notes_document", note).apply()
 }
 Dialog(onDismissRequest = onClose) {
  Card(shape = RoundedCornerShape(18.dp)) {
   Column {
    Row(Modifier.fillMaxWidth().background(Color(0xFF18233C)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
     Text(name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
     IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
    }
    if (name == "Notes") {
     OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth().height(250.dp).padding(10.dp), placeholder = { Text("Start writing…") })
    } else {
     Column(Modifier.padding(18.dp)) {
      val rt = Runtime.getRuntime()
      Text("App heap used: " + ((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)) + " MB")
      Text("Heap cap: " + (rt.maxMemory() / (1024 * 1024)) + " MB")
      Text("CPU cores: " + rt.availableProcessors())
     }
    }
   }
  }
 }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
 Card(modifier, shape = RoundedCornerShape(15.dp)) {
  Column(Modifier.padding(14.dp)) {
   Text(label, color = Quiet, fontSize = 10.sp)
   Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
  }
 }
}

@Composable
private fun ToolCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
 Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(15.dp)) {
  Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
   Icon(icon, null, tint = NovaBlue)
   Spacer(Modifier.width(12.dp))
   Column {
    Text(title, fontWeight = FontWeight.SemiBold)
    Text(subtitle, color = Quiet, fontSize = 11.sp)
   }
  }
 }
}
