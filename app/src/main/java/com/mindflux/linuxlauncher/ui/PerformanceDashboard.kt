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
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.delay
import java.util.Locale

private data class PerformanceSnapshot(
    val totalRam: Long,
    val availableRam: Long,
    val appCpuPercent: Float?,
    val thermal: String,
    val sampledAt: Long
)

@Composable
internal fun PerformanceDashboard() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readPerformanceSnapshot(context, null, null)) }
    var previousCpu by remember { mutableStateOf(Process.getElapsedCpuTime()) }
    var previousWall by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            val cpuNow = Process.getElapsedCpuTime()
            val wallNow = SystemClock.elapsedRealtime()
            val wallDelta = (wallNow - previousWall).coerceAtLeast(1L)
            val cpuDelta = (cpuNow - previousCpu).coerceAtLeast(0L)
            val cpu = (cpuDelta.toFloat() / wallDelta.toFloat() * 100f).coerceIn(0f, 100f)
            snapshot = readPerformanceSnapshot(context, cpu, wallNow)
            previousCpu = cpuNow
            previousWall = wallNow
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF141D29),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF293747))
    ) {
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
                Metric("App CPU", snapshot.appCpuPercent?.let { String.format(Locale.getDefault(), "%.0f%%", it) } ?: "Sampling…")
                Metric("Thermal", snapshot.thermal)
            }
            Text("CPU is this app process only. Thermal data depends on Android version and device support. This panel monitors the phone; it does not change CPU/GPU clocks.", color = Color(0xFF8290A0), fontSize = 9.sp, lineHeight = 13.sp)
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

private fun readPerformanceSnapshot(context: Context, cpu: Float?, sampledAt: Long?): PerformanceSnapshot {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    val thermal = if (Build.VERSION.SDK_INT >= 29) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        thermalLabel(power.currentThermalStatus)
    } else "Unavailable"
    return PerformanceSnapshot(memory.totalMem, memory.availMem, cpu, thermal, sampledAt ?: SystemClock.elapsedRealtime())
}

private fun thermalLabel(status: Int): String = when (status) {
    0 -> "Normal"
    1 -> "Light"
    2 -> "Moderate"
    3 -> "Severe"
    4 -> "Critical"
    5 -> "Emergency"
    6 -> "Shutdown"
    else -> "Unknown"
}

private fun formatRam(bytes: Long): String = String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
