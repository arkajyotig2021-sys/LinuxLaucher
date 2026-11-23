package app.nova.desktop.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import java.io.File

enum class LinuxInstallState { UNSUPPORTED, ENGINE_MISSING, NOT_INSTALLED, READY, FAILED }
data class LinuxInstallStatus(val state: LinuxInstallState, val detail: String)

class LinuxRuntimeManager(context: Context) {
    private val app = context.applicationContext
    private val prefix = File(app.filesDir, "linux-prefix")
    private val home = File(app.filesDir, "linux-home")
    private val cache = File(app.cacheDir, "proot-tmp")
    private val installedRootfs = File(prefix, "var/lib/pr/installed-rootfs/alpine")
    private val ociRootfs = File(prefix, "var/lib/pr/containers/alpine/rootfs")

    fun status(): LinuxInstallStatus {
        if (Build.VERSION.SDK_INT < 28) {
            return LinuxInstallStatus(LinuxInstallState.UNSUPPORTED, "The bundled PRoot engine requires Android 9 (API 28) or newer.")
        }
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
            return LinuxInstallStatus(LinuxInstallState.UNSUPPORTED, "The bundled PRoot runtime currently supports ARM64 only.")
        }
        val libDir = File(app.applicationInfo.nativeLibraryDir)
        val required = listOf("libpr-cli.so", "libproot.so", "libproot-loader.so", "libptyjni.so")
        val missing = required.filterNot { File(libDir, it).isFile }
        if (missing.isNotEmpty()) {
            return LinuxInstallStatus(LinuxInstallState.ENGINE_MISSING, "Linux engine files missing from APK: ${missing.joinToString()}.")
        }
        return if (installedRootfs.isDirectory || ociRootfs.isDirectory) {
            LinuxInstallStatus(LinuxInstallState.READY, "Alpine Linux is installed and the ARM64 PRoot engine is available.")
        } else {
            LinuxInstallStatus(LinuxInstallState.NOT_INSTALLED, "Alpine Linux is ready to install. No root or Termux required.")
        }
    }

    fun startInstall(): ProotLauncher.Session {
        val launcher = createLauncher()
        return launcher.runCommand(listOf("install", "docker.io/library/alpine:latest", "--override-alias", "alpine"))
            ?: error("Could not start the Alpine installer PTY.")
    }

    fun startLogin(): ProotLauncher.Session {
        val current = status()
        require(current.state == LinuxInstallState.READY) { current.detail }
        return createLauncher().startSession("alpine", user = "root")
            ?: error("Could not start the Alpine Linux session.")
    }

    fun startCommand(command: String): ProotLauncher.Session {
        require(command.isNotBlank()) { "Command cannot be empty." }
        val current = status()
        require(current.state == LinuxInstallState.READY) { current.detail }
        return createLauncher().startCustomSession(
            listOf(File(prefix, "bin/pr-cli").absolutePath, "login", "alpine", "--", "/bin/sh", "-lc", command)
        ) ?: error("Could not start the Linux command.")
    }

    private fun createLauncher(): ProotLauncher {
        require(Build.VERSION.SDK_INT >= 28) { "The bundled PRoot engine requires Android 9 (API 28) or newer." }
        require(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) { "The Linux runtime is ARM64-only in this preview." }
        val nativeDir = File(app.applicationInfo.nativeLibraryDir)
        val cli = File(nativeDir, "libpr-cli.so")
        val proot = File(nativeDir, "libproot.so")
        val loader = File(nativeDir, "libproot-loader.so")
        val pty = File(nativeDir, "libptyjni.so")
        require(cli.isFile && proot.isFile && loader.isFile && pty.isFile) {
            "The PRoot engine is not packaged for this device."
        }
        listOf(prefix, home, cache, File(prefix, "bin")).forEach {
            require(it.exists() || it.mkdirs()) { "Cannot create Linux runtime directory: $it" }
        }
        linkExecutable(cli, File(prefix, "bin/pr-cli"))
        linkExecutable(proot, File(prefix, "bin/proot"))

        val host = object : ProotHost {
            override val prefixDir: File = prefix
            override val homeDir: File = home
            override val packageName: String = app.packageName
            override val cacheDir: File = cache
        }
        return ProotLauncher(host)
    }

    private fun linkExecutable(source: File, target: File) {
        if (target.exists()) target.delete()
        runCatching { Os.symlink(source.absolutePath, target.absolutePath) }
            .getOrElse { throw IllegalStateException("Could not link native runtime executable ${target.name}: ${it.message}") }
    }
}
