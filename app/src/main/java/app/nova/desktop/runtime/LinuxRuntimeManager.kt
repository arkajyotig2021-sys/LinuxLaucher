package app.nova.desktop.runtime

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

enum class LinuxInstallState { UNSUPPORTED, NOT_INSTALLED, DOWNLOADING, VERIFYING, EXTRACTING, ROOTFS_READY, ENGINE_MISSING, READY, FAILED }
data class LinuxInstallStatus(val state: LinuxInstallState, val detail: String, val downloadId: Long? = null, val progress: Int = 0)

/** First supported rootfs target: official Alpine minirootfs for Android ARM64 devices. */
class LinuxRuntimeManager(context: Context) {
    private val app = context.applicationContext
    private val downloads = VerifiedDownloadManager(app)
    private val rootfs = File(app.filesDir, "runtimes/alpine/rootfs")
    private val archiveName = "alpine-minirootfs-3.24.4-aarch64.tar.gz"
    private val archiveUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/$archiveName"
    private val checksumUrl = "$archiveUrl.sha256"
    private val trustedHosts = setOf("dl-cdn.alpinelinux.org")

    fun status(): LinuxInstallStatus {
        if (Build.SUPPORTED_ABIS.none { it == "arm64-v8a" }) {
            return LinuxInstallStatus(LinuxInstallState.UNSUPPORTED, "This first Linux runtime supports ARM64 only.")
        }
        if (!rootfs.resolve(".nova-rootfs").isFile) {
            return LinuxInstallStatus(LinuxInstallState.NOT_INSTALLED, "Alpine Linux 3.24.4 ARM64 root filesystem is not installed.")
        }
        val proot = File(app.filesDir, "runtime/bin/proot")
        return if (proot.canExecute()) LinuxInstallStatus(LinuxInstallState.READY, "Alpine rootfs and PRoot backend are present.")
        else LinuxInstallStatus(LinuxInstallState.ENGINE_MISSING, "Alpine rootfs is installed. PRoot executable is not bundled in this build yet.")
    }

    /** Fetch the upstream SHA-256 sidecar over TLS before queuing the rootfs archive. */
    fun beginInstall(): Long {
        require(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) { "Alpine ARM64 rootfs requires an ARM64 Android device." }
        val connection = (URL(checksumUrl).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
        }
        val checksum = try {
            require(connection.responseCode == 200) { "Alpine checksum server returned HTTP ${connection.responseCode}." }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
        val expected = Regex("(?i)\\b[0-9a-f]{64}\\b").find(checksum)?.value
            ?: error("Alpine checksum sidecar did not contain a SHA-256 digest.")
        return downloads.enqueue(archiveUrl, archiveName, expected, trustedHosts)
    }

    fun inspect(downloadId: Long): VerifiedDownloadStatus = downloads.inspect(downloadId)

    fun finishInstall(downloadId: Long): LinuxInstallStatus {
        val progress = downloads.inspect(downloadId)
        require(progress.state == VerifiedDownloadState.VERIFYING) { "Rootfs download is not complete." }
        val checksumResponse = (URL(checksumUrl).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
        }
        val checksumText = try {
            require(checksumResponse.responseCode == 200) { "Could not refresh Alpine checksum." }
            checksumResponse.inputStream.bufferedReader().use { it.readText() }
        } finally { checksumResponse.disconnect() }
        val expected = Regex("(?i)\\b[0-9a-f]{64}\\b").find(checksumText)?.value
            ?: error("Invalid Alpine SHA-256 sidecar.")
        val verified = downloads.verify(downloadId, expected)
        require(verified.state == VerifiedDownloadState.VERIFIED) { verified.detail }
        val uri = downloads.downloadedUri(downloadId) ?: error("Verified rootfs download URI is missing.")
        val staged = File(app.cacheDir, archiveName)
        app.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(staged).use { output -> input.copyTo(output) }
        } ?: error("Cannot read verified rootfs archive.")
        try {
            LinuxRootfsInstaller().installVerifiedTarGz(staged, expected, rootfs)
        } finally {
            staged.delete()
            downloads.cancel(downloadId)
        }
        return status()
    }

    fun launchCommand(command: String): Process {
        require(command.isNotBlank()) { "Command cannot be empty." }
        val current = status()
        require(current.state == LinuxInstallState.READY) { current.detail }
        val proot = File(app.filesDir, "runtime/bin/proot")
        return ProcessBuilder(
            proot.absolutePath, "--kill-on-exit", "--link2symlink", "--rootfs=${rootfs.absolutePath}",
            "--cwd=/root", "--bind=/dev", "--bind=/proc", "--bind=/sys", "/bin/sh", "-lc", command
        ).directory(rootfs).redirectErrorStream(true).start()
    }
}
