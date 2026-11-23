package app.nova.desktop.runtime

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import java.security.MessageDigest
import java.util.Locale

enum class VerifiedDownloadState { QUEUED, RUNNING, VERIFYING, VERIFIED, FAILED, MISSING }
data class VerifiedDownloadStatus(
    val id: Long,
    val state: VerifiedDownloadState,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val detail: String = ""
) {
    val progressPercent: Int
        get() = if (totalBytes <= 0) 0 else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}

/**
 * Uses Android's managed download service, then verifies the completed artifact against
 * a SHA-256 value supplied by a trusted, pinned runtime manifest. A download is not
 * installable until verify() returns VERIFIED.
 */
class VerifiedDownloadManager(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(
        source: String,
        fileName: String,
        expectedSha256: String,
        trustedHosts: Set<String>
    ): Long {
        val uri = Uri.parse(source)
        require(uri.scheme == "https") { "Only HTTPS downloads are accepted." }
        val host = uri.host?.lowercase(Locale.ROOT) ?: error("Download URL has no host.")
        require(trustedHosts.any { it.equals(host, ignoreCase = true) }) { "Download host is not in the trusted manifest." }
        require(expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) { "A valid SHA-256 checksum is required." }
        require(fileName.matches(Regex("^[A-Za-z0-9._-]{1,120}$"))) { "Invalid download file name." }

        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setDescription("NOVA runtime component")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(app, "verified-downloads", fileName)
        return manager.enqueue(request)
    }

    fun inspect(id: Long): VerifiedDownloadStatus {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return VerifiedDownloadStatus(id, VerifiedDownloadState.MISSING)
        cursor.use {
            if (!it.moveToFirst()) return VerifiedDownloadStatus(id, VerifiedDownloadState.MISSING)
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            return when (status) {
                DownloadManager.STATUS_PENDING -> VerifiedDownloadStatus(id, VerifiedDownloadState.QUEUED, bytes, total)
                DownloadManager.STATUS_RUNNING -> VerifiedDownloadStatus(id, VerifiedDownloadState.RUNNING, bytes, total)
                DownloadManager.STATUS_PAUSED -> VerifiedDownloadStatus(id, VerifiedDownloadState.QUEUED, bytes, total, "Paused by Android download service")
                DownloadManager.STATUS_SUCCESSFUL -> VerifiedDownloadStatus(id, VerifiedDownloadState.VERIFYING, bytes, total, "Downloaded; checksum verification required")
                DownloadManager.STATUS_FAILED -> VerifiedDownloadStatus(id, VerifiedDownloadState.FAILED, bytes, total, "Android DownloadManager error $reason")
                else -> VerifiedDownloadStatus(id, VerifiedDownloadState.MISSING, bytes, total, "Unknown download state")
            }
        }
    }

    fun verify(id: Long, expectedSha256: String): VerifiedDownloadStatus {
        require(expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) { "Invalid expected SHA-256." }
        val current = inspect(id)
        if (current.state != VerifiedDownloadState.VERIFYING) return current
        val uri = manager.getUriForDownloadedFile(id)
            ?: return current.copy(state = VerifiedDownloadState.FAILED, detail = "Downloaded file URI is unavailable.")
        val actual = app.contentResolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } ?: return current.copy(state = VerifiedDownloadState.FAILED, detail = "Downloaded file cannot be read.")
        return if (actual.equals(expectedSha256, ignoreCase = true)) {
            current.copy(state = VerifiedDownloadState.VERIFIED, detail = "SHA-256 verified.")
        } else {
            manager.remove(id)
            current.copy(state = VerifiedDownloadState.FAILED, detail = "Checksum mismatch; untrusted download deleted.")
        }
    }

    fun cancel(id: Long): Boolean = manager.remove(id) > 0
    fun downloadedUri(id: Long): Uri? = manager.getUriForDownloadedFile(id)
}
