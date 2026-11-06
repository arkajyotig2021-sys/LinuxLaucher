package com.mindflux.linuxlauncher.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindflux.linuxlauncher.runtime.LauncherPreferences
import com.mindflux.linuxlauncher.runtime.PerformancePolicy
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private data class PerformanceSnapshot(val totalRam: Long, val availableRam: Long, val appCpu: Float?, val systemCpu: Float?, val thermal: String, val headroom: String, val mode: String, val adaptation: String)
private data class CpuCounters(val total: Long, val idle: Long)

@Composable
internal fun PerformanceDashboard() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readSnapshot(context, null, null)) }
    var previousAppCpu by remember { mutableStateOf(Process.getElapsedCpuTime()) }
    var previousWall by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var previousSystem by remember { mutableStateOf(readCpuCounters()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            val appNow = Process.getElapsedCpuTime()
            val wallNow = SystemClock.elapsedRealtime()
            val wallDelta = (wallNow - previousWall).coerceAtLeast(1L)
            val appDelta = (appNow - previousAppCpu).coerceAtLeast(0L)
            val appCpu = (appDelta.toFloat() / wallDelta.toFloat() * 100f).coerceIn(0f, 100f)
            val systemNow = readCpuCounters()
            val systemCpu = if (systemNow != null && previousSystem != null) {
                val total = (systemNow.total - previousSystem!!.total).coerceAtLeast(1L)
                val idle = (systemNow.idle - previousSystem!!.idle).coerceAtLeast(0L)
                ((total - idle).toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
            } else null
            snapshot = readSnapshot(context, appCpu, systemCpu)
            previousAppCpu = appNow
            previousWall = wallNow
            previousSystem = systemNow
        }
    }

    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF141D29), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF293747))) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LIVE PERFORMANCE", color = Color(0xFF69E6B7), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("2-SECOND REFRESH", color = Color(0xFF8290A0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Text("${formatRam(snapshot.totalRam - snapshot.availableRam)} / ${formatRam(snapshot.totalRam)}", color = Color(0xFFF2F5F8), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Physical RAM in use", color = Color(0xFF8290A0), fontSize = 10.sp)
            val ramRatio = ((snapshot.totalRam - snapshot.availableRam).toFloat() / snapshot.totalRam.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFF2A3543), RoundedCornerShape(8.dp))) {
                Box(Modifier.fillMaxWidth(ramRatio).height(7.dp).background(Color(0xFF69E6B7), RoundedCornerShape(8.dp)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Available RAM", formatRam(snapshot.availableRam))
                Metric("System CPU", snapshot.systemCpu?.let { "${it.toInt()}%" } ?: "Unavailable")
                Metric("App CPU", snapshot.appCpu?.let { "${it.toInt()}%" } ?: "Sampling…")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Thermal", snapshot.thermal)
                Metric("Headroom", snapshot.headroom)
                Metric("Effective mode", snapshot.mode)
            }
            Text(snapshot.adaptation, color = Color(0xFF69E6B7), fontSize = 9.sp)
            Text("System CPU is sampled from /proc/stat when Android permits access; app CPU is this app process only. Headroom estimates responsiveness from memory and thermal state, not measured frame latency. Android may restrict telemetry and scheduling.", color = Color(0xFF8290A0), fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color(0xFF8290A0), fontSize = 9.sp)
        Text(value, color = Color(0xFFF2F5F8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun readSnapshot(context: Context, appCpu: Float?, systemCpu: Float?): PerformanceSnapshot {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    val thermalCode = if (Build.VERSION.SDK_INT >= 29) (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else -1
    val thermal = when (thermalCode) { 0 -> "Normal"; 1 -> "Light"; 2 -> "Moderate"; 3 -> "Severe"; 4 -> "Critical"; 5 -> "Emergency"; 6 -> "Shutdown"; else -> "Unavailable" }
    val policy = PerformancePolicy.resolve(context)
    val headroom = when { thermalCode >= 3 || memory.availMem < 350L * 1024L * 1024L -> "Constrained"; thermalCode >= 2 || memory.availMem < 700L * 1024L * 1024L -> "Reduced"; else -> "Good" }
    val adaptive = if (LauncherPreferences.adaptive(context)) "Automatic adaptation ON · ${policy.reason}" else "Automatic adaptation OFF"
    return PerformanceSnapshot(memory.totalMem, memory.availMem, appCpu, systemCpu, thermal, headroom, policy.mode.label, adaptive)
}

private fun readCpuCounters(): CpuCounters? = runCatching {
    val parts = File("/proc/stat").useLines { lines -> lines.first().trim().split(Regex("\\s+")) }
    val values = parts.drop(1).map { it.toLong() }
    CpuCounters(values.sum(), values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L })
}.getOrNull()

private fun formatRam(bytes: Long): String = String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
