package com.mindflux.linuxlauncher.runtime

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class PerformanceMode(val label: String) { SMOOTH("Smooth"), BALANCED("Balanced"), BATTERY_SAVER("Battery Saver") }

data class RuntimePerformancePolicy(val mode: PerformanceMode, val threadPriority: Int, val desktopGeometry: String, val reason: String)

object LauncherPreferences {
    private const val PREFS = "linux_launcher_preferences"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun mode(context: Context): PerformanceMode = runCatching { PerformanceMode.valueOf(prefs(context).getString("performance_mode", PerformanceMode.SMOOTH.name)!!) }.getOrDefault(PerformanceMode.SMOOTH)
    fun setMode(context: Context, mode: PerformanceMode) { prefs(context).edit().putString("performance_mode", mode.name).apply() }
    fun adaptive(context: Context): Boolean = prefs(context).getBoolean("adaptive_performance", true)
    fun setAdaptive(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("adaptive_performance", enabled).apply() }
    fun externalNote(context: Context): String = prefs(context).getString("external_storage_note", "") ?: ""
    fun setExternalNote(context: Context, value: String) { prefs(context).edit().putString("external_storage_note", value).apply() }
}

object PerformancePolicy {
    fun resolve(context: Context): RuntimePerformancePolicy {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val thermal = if (Build.VERSION.SDK_INT >= 29) (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else 0
        val selected = LauncherPreferences.mode(context)
        val constrained = memory.availMem < 650L * 1024L * 1024L || thermal >= 3
        val warm = memory.availMem < 1024L * 1024L * 1024L || thermal >= 2
        val effective = if (LauncherPreferences.adaptive(context)) {
            when { constrained -> PerformanceMode.BATTERY_SAVER; warm && selected == PerformanceMode.SMOOTH -> PerformanceMode.BALANCED; else -> selected }
        } else selected
        val priority = when (effective) {
            PerformanceMode.SMOOTH -> android.os.Process.THREAD_PRIORITY_DEFAULT
            PerformanceMode.BALANCED -> android.os.Process.THREAD_PRIORITY_LESS_FAVORABLE
            PerformanceMode.BATTERY_SAVER -> android.os.Process.THREAD_PRIORITY_BACKGROUND
        }
        val geometry = when (effective) {
            PerformanceMode.SMOOTH -> "1280x720"
            PerformanceMode.BALANCED -> "960x540"
            PerformanceMode.BATTERY_SAVER -> "800x600"
        }
        val reason = when {
            constrained -> "Reduced workload: low available RAM or high thermal status"
            warm && effective != selected -> "Balanced automatically to protect responsiveness"
            else -> "Using your selected performance mode"
        }
        return RuntimePerformancePolicy(effective, priority, geometry, reason)
    }
}

object LauncherNotifications {
    private const val CHANNEL = "linux_launcher_activity"
    private const val PROGRESS_ID = 4101
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Linux Launcher activity", NotificationManager.IMPORTANCE_LOW).apply { description = "Linux downloads, installations, updates and errors" })
        }
    }
    private fun post(context: Context, id: Int, title: String, message: String, progress: Pair<Int, Int>? = null, error: Boolean = false) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(if (error) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (error) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(error || progress == null)
        if (progress != null) builder.setProgress(progress.second.coerceAtLeast(1), progress.first.coerceIn(0, progress.second.coerceAtLeast(1)), false)
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }
    fun progress(context: Context, title: String, message: String, current: Long, total: Long) {
        val pair = if (total > 0) ((current.toDouble() / total.toDouble()).coerceIn(0.0, 1.0) * 10_000).toInt() to 10_000 else null
        post(context, PROGRESS_ID, title, message, pair)
    }
    fun finished(context: Context, title: String, message: String) {
        post(context, PROGRESS_ID, title, message)
    }
    fun error(context: Context, title: String, message: String) {
        post(context, System.currentTimeMillis().toInt(), title, message, error = true)
    }
    fun updateAvailable(context: Context, distro: String, message: String) {
        post(context, (System.currentTimeMillis() + 1).toInt(), "$distro updates available", message)
    }
    fun updateCompleted(context: Context, distro: String) {
        post(context, (System.currentTimeMillis() + 2).toInt(), "$distro updated", "The requested Linux update completed.")
    }
}

data class LauncherError(val time: Long, val category: String, val details: String)

object LauncherErrorHistory {
    private const val PREFS = "linux_launcher_errors"
    fun record(context: Context, category: String, details: String) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = JSONArray(prefs.getString("items", "[]") ?: "[]")
            val next = JSONArray()
            next.put(JSONObject().put("time", System.currentTimeMillis()).put("category", category).put("details", details.take(6000)))
            for (i in 0 until minOf(old.length(), 39)) next.put(old.getJSONObject(i))
            prefs.edit().putString("items", next.toString()).apply()
        }
    }
    fun all(context: Context): List<LauncherError> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", "[]") ?: "[]")
        (0 until array.length()).map { val o = array.getJSONObject(it); LauncherError(o.optLong("time"), o.optString("category"), o.optString("details")) }
    }.getOrDefault(emptyList())
    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("items").apply() }
    fun diagnostics(context: Context): String {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val policy = PerformancePolicy.resolve(context)
        val thermal = if (Build.VERSION.SDK_INT >= 29) (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus.toString() else "Unavailable (Android API below 29)"
        val installed = LinuxSystemRegistry.list(context).joinToString().ifBlank { "None" }
        return "Linux Launcher diagnostics\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nABIs: ${Build.SUPPORTED_ABIS.joinToString()}\nRAM: ${memory.availMem / 1048576} MB available / ${memory.totalMem / 1048576} MB total\nThermal status code: $thermal\nPerformance mode: ${policy.mode.label}\nAdaptation: ${LauncherPreferences.adaptive(context)}\nInstalled Linux systems: $installed"
    }
}

object LauncherSessionRecovery {
    private const val PREFS = "linux_launcher_session"
    fun markActive(context: Context, distroId: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("active", true).putString("distro", distroId).apply() }
    fun pending(context: Context): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.getBoolean("active", false)) p.getString("distro", null) else null
    }
    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("active", false).remove("distro").apply() }
}

object LinuxSystemRegistry {
    fun list(context: Context): List<String> = File(context.filesDir, "linux-containers").listFiles()?.filter { dir ->
        val root = File(dir, "rootfs")
        root.isDirectory && (File(root, "bin").isDirectory || File(root, "usr/bin").isDirectory)
    }?.map { it.name }?.sorted() ?: emptyList()
    fun remove(context: Context, id: String): Boolean {
        require(id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")))
        val base = File(context.filesDir, "linux-containers").canonicalFile
        val target = File(base, id).canonicalFile
        require(target.parentFile == base) { "Invalid Linux system path" }
        return !target.exists() || target.deleteRecursively()
    }
}

object LinuxExternalFiles {
    fun sharedDirectory(context: Context, distroId: String): File {
        val root = File(context.filesDir, "linux-containers/$distroId/rootfs").canonicalFile
        val shared = File(root, "root/shared").canonicalFile
        require(shared.path.startsWith(root.path + File.separator))
        if (!shared.exists()) shared.mkdirs()
        return shared
    }
    fun files(context: Context, distroId: String): List<File> = sharedDirectory(context, distroId).listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    fun importFile(context: Context, distroId: String, uri: Uri): File {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.take(100)?.takeIf { it.isNotBlank() } ?: "imported-file"
        val target = File(sharedDirectory(context, distroId), name).canonicalFile
        require(target.parentFile == sharedDirectory(context, distroId).canonicalFile)
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read selected external file")
        input.use { source -> FileOutputStream(target).use { source.copyTo(it) } }
        return target
    }
    fun export(context: Context, source: File, destination: Uri) {
        require(source.isFile && source.canonicalPath.startsWith(File(context.filesDir, "linux-containers").canonicalPath + File.separator))
        val output = context.contentResolver.openOutputStream(destination) ?: error("Cannot write selected external destination")
        output.use { target -> FileInputStream(source).use { it.copyTo(target) } }
    }
}


object LinuxUpdateChecker {
    suspend fun check(context: Context, distroId: String): Int = withContext(Dispatchers.IO) {
        val binary = RuntimeBinaryLocator(context).prootExecutable() ?: error("PRoot engine is missing from this build")
        val root = RuntimeCommandBuilder.rootfsDirectory(context.filesDir, distroId)
        val command = "if command -v apt-get >/dev/null 2>&1; then apt-get update -qq && apt list --upgradable 2>/dev/null; else echo 'Automatic update checks currently support apt-based Debian/Ubuntu systems.'; exit 42; fi"
        val process = LinuxRuntimeSession(binary, root, PerformancePolicy.resolve(context).threadPriority).startCommand(command)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        if (exit != 0) error(output.lineSequence().lastOrNull { it.isNotBlank() } ?: "Update check failed (exit $exit)")
        val count = output.lineSequence().count { it.contains("[upgradable from ") }
        if (count > 0) LauncherNotifications.updateAvailable(context, distroId, "$count package update(s) are available. Review them in the Linux terminal before upgrading.")
        else LauncherNotifications.finished(context, "$distroId is up to date", "No apt package updates were reported.")
        count
    }
}
