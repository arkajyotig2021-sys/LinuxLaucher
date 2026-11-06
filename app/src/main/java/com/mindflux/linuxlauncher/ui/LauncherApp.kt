package com.mindflux.linuxlauncher.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindflux.linuxlauncher.data.Distro
import com.mindflux.linuxlauncher.data.DistroCatalog
import java.util.Locale

private enum class Tab { HOME, EXPLORE, DEVICE, SETTINGS }
private data class DeviceProfile(
    val brand: String, val model: String, val android: String, val api: Int,
    val soc: String, val abi: String, val cores: Int, val ram: Long,
    val availableRam: Long, val storage: Long, val freeStorage: Long,
    val resolution: String, val touch: Boolean
)

@Composable
fun LauncherApp() {
    val context = LocalContext.current
    val device = remember(context) { detectDevice(context) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Distro?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, secondary = Lilac, background = Background,
        surface = SurfaceDark, onSurface = White, onBackground = White
    )) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF111A25), tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = tab == Tab.HOME,
                        onClick = { tab = Tab.HOME },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                        label = { Text("Home", maxLines = 1) },
                        colors = navigationItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == Tab.EXPLORE,
                        onClick = { tab = Tab.EXPLORE },
                        icon = { Icon(Icons.Default.GridView, contentDescription = "Explore") },
                        label = { Text("Explore", maxLines = 1) },
                        colors = navigationItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == Tab.DEVICE,
                        onClick = { tab = Tab.DEVICE },
                        icon = { Icon(Icons.Default.Memory, contentDescription = "My device") },
                        label = { Text("My device", maxLines = 1) },
                        colors = navigationItemColors()
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SETTINGS,
                        onClick = { tab = Tab.SETTINGS },
                        icon = { Icon(Icons.Default.Tune, contentDescription = "Settings") },
                        label = { Text("Settings", maxLines = 1) },
                        colors = navigationItemColors()
                    )
                }
            }
        ) { inset ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset)
                    .background(Background)
            ) {
                when (tab) {
                    Tab.HOME -> Home(device, { tab = Tab.EXPLORE }, { tab = Tab.DEVICE }, Modifier.fillMaxSize())
                    Tab.EXPLORE -> Explore(query, { query = it }, { selected = it }, Modifier.fillMaxSize())
                    Tab.DEVICE -> DeviceDetails(device, Modifier.fillMaxSize())
                    Tab.SETTINGS -> Settings(Modifier.fillMaxSize())
                }
            }
        }
    }
    selected?.let { distro ->
        AlertDialog(
            onDismissRequest = { selected = null }, containerColor = SurfaceDark,
            title = { Text(distro.name, color = White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(distro.description, color = Muted)
                    Detail("Family", distro.family)
                    Detail("Desktop options", distro.desktopHint)
                    Detail("Architecture", distro.architecture)
                    Text("Catalogue information only. Linux installation is not implemented in this build.", color = Color(0xFFFFC98B), fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close", color = Mint) } }
        )
    }
}

@Composable
private fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Mint,
    selectedTextColor = Mint,
    unselectedIconColor = Muted,
    unselectedTextColor = Muted,
    indicatorColor = Color(0xFF203A34)
)

@Composable
private fun Home(d: DeviceProfile, explore: () -> Unit, devicePage: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LINUX LAUNCHER  /  MIND FLUX", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Text("A new kind of\nLinux workspace.", color = White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your phone. Your Linux environment.", color = Muted, fontSize = 12.sp)
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF244B42), Color(0xFF292746)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Terminal, null, tint = Mint, modifier = Modifier.size(25.dp))
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1D4039), Color(0xFF202B40), Color(0xFF302747))))
                    .border(1.dp, Color(0xFF3C554F), RoundedCornerShape(26.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF285344), shape = CircleShape) {
                        Text("●  DEVICE PROFILE DETECTED", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.AutoAwesome, null, tint = Lilac)
                }
                Text("Meet your\nLinux potential.", color = White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold)
                Text("A hardware-aware starting point for ${d.model}.", color = Color(0xFFC5D0D0), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Pill("${gb(d.ram)} RAM")
                    Pill(d.abi.substringBefore(','))
                    Pill("${d.cores} CPU cores")
                }
                Button(onClick = explore, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Background), shape = RoundedCornerShape(13.dp)) {
                    Text("Explore Linux distros", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(7.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                }
            }
        }
        item {
            SectionTitle("Hardware snapshot", "READ FROM ANDROID")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Physical RAM", gb(d.ram), "${gb(d.availableRam)} available", Icons.Default.Memory, Modifier.weight(1f))
                StatCard("Internal storage", gb(d.storage), "${gb(d.freeStorage)} free", Icons.Default.Storage, Modifier.weight(1f))
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().clickable(onClick = devicePage), color = SurfaceDark, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Smartphone, null, tint = Mint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${d.brand} ${d.model}", color = White, fontWeight = FontWeight.SemiBold)
                        Text("View the complete detected device profile", color = Muted, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
        item {
            SectionTitle("Designed around your hardware", "NO ROOT")
            Surface(color = SurfaceDark, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Feature(Icons.Default.Memory, "Real memory readings", "Uses Android ActivityManager.")
                    Feature(Icons.Default.Storage, "Real storage readings", "Reads the internal data partition.")
                    Feature(Icons.Default.Security, "No root access", "Device profile is read-only.")
                }
            }
        }
        item { Text("This phase is the launcher interface and hardware detection. Linux installation and desktop runtime are not available yet.", color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun Explore(query: String, change: (String) -> Unit, select: (Distro) -> Unit, modifier: Modifier = Modifier) {
    val results = remember(query) { DistroCatalog.search(query) }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text("THE COLLECTION", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Find your distro.", color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Explore distributions and desktop options.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query, onValueChange = change, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search distributions", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { change("") }) { Icon(Icons.Default.Close, "Clear", tint = Muted) } },
            singleLine = true, shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Mint, unfocusedBorderColor = Stroke,
                focusedTextColor = White, unfocusedTextColor = White, cursorColor = Mint
            )
        )
        Spacer(Modifier.height(12.dp))
        Text("${results.size} DISTRIBUTIONS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(top = 9.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.id }) { distro ->
                val initial = distro.name.firstOrNull()?.uppercase() ?: "L"
                Surface(Modifier.fillMaxWidth().clickable { select(distro) }, color = SurfaceDark, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF294940), Color(0xFF302C4D)))), contentAlignment = Alignment.Center) {
                            Text(initial, color = Mint, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(distro.name, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(distro.family, color = Muted, fontSize = 10.sp)
                            Text(distro.desktopHint, color = Soft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("DETAILS", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                    }
                }
            }
            if (results.isEmpty()) item { Text("No results. Try another search.", color = Muted, modifier = Modifier.padding(18.dp)) }
        }
    }
}

@Composable
private fun DeviceDetails(d: DeviceProfile, modifier: Modifier = Modifier) {
    val ramUsed = (d.ram - d.availableRam).coerceAtLeast(0)
    val storageUsed = (d.storage - d.freeStorage).coerceAtLeast(0)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Text("HARDWARE INTELLIGENCE", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Text("Your device.", color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Live values queried from Android system services—not fixed demo numbers.", color = Muted, fontSize = 11.sp)
        }
        item {
            InfoCard("DEVICE IDENTITY") {
                Text("${d.brand} ${d.model}", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Detail("Android version", d.android)
                Detail("API level", d.api.toString())
                Detail("CPU architecture", d.abi)
                Detail("SoC / board identifier", d.soc)
                Detail("Available CPU cores", d.cores.toString())
                Detail("Screen resolution", d.resolution)
                Detail("Touchscreen", if (d.touch) "Detected" else "Not reported")
            }
        }
        item {
            InfoCard("PHYSICAL MEMORY") {
                Text(gb(d.ram), color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Total RAM reported by Android", color = Muted, fontSize = 10.sp)
                Usage(ramUsed, d.ram)
                Detail("Available now", gb(d.availableRam))
                Detail("Approx. in use", gb(ramUsed))
                Text("Virtual RAM expansion is not counted as physical RAM.", color = Muted, fontSize = 10.sp)
            }
        }
        item {
            InfoCard("INTERNAL STORAGE") {
                Text(gb(d.storage), color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Total data partition capacity", color = Muted, fontSize = 10.sp)
                Usage(storageUsed, d.storage)
                Detail("Free space", gb(d.freeStorage))
                Detail("Approx. used", gb(storageUsed))
            }
        }
        item { Text("GPU name is not reliably exposed through Android's public APIs; this app deliberately does not guess it.", color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun Settings(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("PREFERENCES", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Settings.", color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Setting(Icons.Default.Palette, "Appearance", "Obsidian dark theme")
        Setting(Icons.Default.Notifications, "Notifications", "Update alerts planned")
        Setting(Icons.Default.Tune, "Performance", "Runtime controls planned")
        Setting(Icons.Default.Info, "About", "Linux Launcher · Phase 1")
        Text("Installer, runtime, desktop session and live tuning are future development phases.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceDark).border(1.dp, Stroke, RoundedCornerShape(20.dp)).padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        content()
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text(value, color = White, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun Usage(used: Long, total: Long) {
    val ratio = used.toFloat() / total.coerceAtLeast(1).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color(0xFF2A3543))) {
            Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Mint, Lilac))))
        }
        Text("${(ratio * 100).toInt().coerceIn(0, 100)}% used", color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun SectionTitle(title: String, eyebrow: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(eyebrow, color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun StatCard(title: String, value: String, sub: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(SurfaceDark).border(1.dp, Stroke, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = Mint, modifier = Modifier.size(18.dp))
        Text(value, color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = Soft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun Pill(text: String) {
    Surface(color = Color(0x3326E6B1), shape = CircleShape) {
        Text(text, color = Color(0xFFD5F8EB), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF203A34)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Mint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Setting(icon: ImageVector, title: String, subtitle: String) {
    Surface(color = SurfaceDark, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Mint)
            Spacer(Modifier.width(13.dp))
            Column {
                Text(title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

private fun detectDevice(context: Context): DeviceProfile {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
    val stat = StatFs(Environment.getDataDirectory().absolutePath)
    val metrics = context.resources.displayMetrics
    val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() } else null
    return DeviceProfile(
        brand = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) },
        model = Build.MODEL,
        android = Build.VERSION.RELEASE,
        api = Build.VERSION.SDK_INT,
        soc = soc ?: Build.BOARD.ifBlank { Build.HARDWARE },
        abi = Build.SUPPORTED_ABIS.joinToString(", "),
        cores = Runtime.getRuntime().availableProcessors(),
        ram = memory.totalMem,
        availableRam = memory.availMem,
        storage = stat.totalBytes,
        freeStorage = stat.availableBytes,
        resolution = "${metrics.widthPixels} × ${metrics.heightPixels}",
        touch = context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    )
}

private fun gb(bytes: Long) = String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))

private val Background = Color(0xFF0B1018)
private val SurfaceDark = Color(0xFF141D29)
private val Stroke = Color(0xFF293747)
private val Mint = Color(0xFF69E6B7)
private val Lilac = Color(0xFFAA9BFF)
private val White = Color(0xFFF2F5F8)
private val Soft = Color(0xFFBAC5D1)
private val Muted = Color(0xFF8290A0)
