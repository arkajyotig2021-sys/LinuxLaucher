package app.nova.desktop.runtime

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

enum class WindowsBackendState { UNSUPPORTED, BACKEND_MISSING, READY }
data class WindowsBackendStatus(val state: WindowsBackendState, val detail: String, val box64: File? = null, val wine: File? = null)

/** Stages user-selected installers privately and runs them only when a compatible Wine/Box64 bundle exists. */
class WindowsRuntimeManager(context: Context) {
    private val app = context.applicationContext
    private val runtime = File(app.filesDir, "runtime")
    private val box64 = File(runtime, "bin/box64")
    private val wine = File(runtime, "wine/bin/wine")
    private val prefix = File(app.filesDir, "runtimes/windows/prefix")

    fun status(): WindowsBackendStatus {
        if (!android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            return WindowsBackendStatus(WindowsBackendState.UNSUPPORTED, "The initial Windows backend target is ARM64.")
        }
        return if (box64.canExecute() && wine.canExecute()) {
            WindowsBackendStatus(WindowsBackendState.READY, "Wine and Box64 binaries detected.", box64, wine)
        } else {
            WindowsBackendStatus(WindowsBackendState.BACKEND_MISSING, "Wine/Box64 and a validated Windows compatibility bundle are not included in this build.")
        }
    }

    fun stageInstaller(uri: Uri): File {
        val name = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "installer.exe"
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        val extension = safeName.substringAfterLast('.', "").lowercase()
        require(extension == "exe" || extension == "msi") { "Choose a Windows .exe or .msi installer." }
        val targetDir = File(app.filesDir, "runtimes/windows/installers")
        require(targetDir.exists() || targetDir.mkdirs()) { "Cannot create Windows installer folder." }
        val target = File(targetDir, safeName)
        val temp = File(targetDir, safeName + ".partial")
        app.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 1024L * 1024 * 1024) { "Installer exceeds the 1 GB safety limit." }
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("Cannot read selected installer.")
        require(temp.length() > 0) { "Installer file is empty." }
        if (target.exists()) target.delete()
        require(temp.renameTo(target)) { "Could not stage installer." }
        return target
    }

    fun install(stagedInstaller: File): Process {
        require(stagedInstaller.canonicalPath.startsWith(File(app.filesDir, "runtimes/windows/installers").canonicalPath + File.separator)) {
            "Installer must be staged inside NOVA's private Windows installer folder."
        }
        require(stagedInstaller.isFile) { "Installer file is missing." }
        val backend = status()
        require(backend.state == WindowsBackendState.READY) { backend.detail }
        require(prefix.exists() || prefix.mkdirs()) { "Cannot create Wine prefix." }
        val isMsi = stagedInstaller.extension.equals("msi", true)
        val args = if (isMsi) listOf("msiexec", "/i", stagedInstaller.absolutePath) else listOf(stagedInstaller.absolutePath)
        return ProcessBuilder(listOf(backend.box64!!.absolutePath, backend.wine!!.absolutePath) + args)
            .directory(prefix)
            .redirectErrorStream(true)
            .apply {
                environment()["WINEPREFIX"] = prefix.absolutePath
                environment()["HOME"] = app.filesDir.absolutePath
                environment()["BOX64_LOG"] = "0"
            }
            .start()
    }

    fun installerSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
