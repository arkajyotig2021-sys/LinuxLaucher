package app.nova.desktop.files

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/** SAF-only operations, limited to folders explicitly granted by the user. */
class SpatialFileOperations(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun list(directory: DocumentFile): List<DocumentFile> =
        directory.listFiles().sortedWith(compareByDescending<DocumentFile> { it.isDirectory }
            .thenBy { it.name?.lowercase().orEmpty() })

    fun createFolder(parent: DocumentFile, name: String): DocumentFile =
        parent.createDirectory(validName(name)) ?: throw IOException("Storage provider could not create folder.")

    fun createFile(parent: DocumentFile, name: String): DocumentFile =
        parent.createFile(mimeFor(name), validName(name)) ?: throw IOException("Storage provider could not create file.")

    fun rename(file: DocumentFile, newName: String): Boolean = file.renameTo(validName(newName))
    fun delete(file: DocumentFile): Boolean = file.delete()

    fun copy(source: DocumentFile, destination: DocumentFile): DocumentFile {
        require(destination.isDirectory && destination.canWrite()) { "Destination is not writable." }
        require(source.uri != destination.uri && !isInside(destination, source.uri)) {
            "A folder cannot be copied into itself."
        }
        val name = validName(source.name ?: "Untitled")
        if (source.isDirectory) {
            val target = destination.createDirectory(name) ?: throw IOException("Could not create destination folder.")
            try { source.listFiles().forEach { copy(it, target) } }
            catch (e: Exception) { target.delete(); throw e }
            return target
        }
        val target = destination.createFile(resolver.getType(source.uri) ?: mimeFor(name), name)
            ?: throw IOException("Could not create destination file.")
        try {
            val input = resolver.openInputStream(source.uri) ?: throw IOException("Source is unreadable.")
            val output = resolver.openOutputStream(target.uri, "wt") ?: throw IOException("Destination is unwritable.")
            input.use { src -> output.use { dst -> src.copyTo(dst); dst.flush() } }
        } catch (e: Exception) { target.delete(); throw e }
        return target
    }

    /** Cross-folder moves use copy-then-delete because SAF providers need not support atomic rename. */
    fun move(source: DocumentFile, destination: DocumentFile): DocumentFile {
        val copy = copy(source, destination)
        if (!source.delete()) {
            copy.delete()
            throw IOException("Original could not be removed; copied item was rolled back.")
        }
        return copy
    }

    fun shareIntent(file: DocumentFile): Intent = Intent(Intent.ACTION_SEND).apply {
        type = resolver.getType(file.uri) ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun openIntent(file: DocumentFile): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.uri, resolver.getType(file.uri) ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun isInside(candidate: DocumentFile, ancestor: android.net.Uri): Boolean {
        var current: DocumentFile? = candidate
        while (current != null) {
            if (current.uri == ancestor) return true
            current = current.parentFile
        }
        return false
    }

    private fun validName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty() && name != "." && name != ".." &&
            '/' !in name && '\\' !in name && name.length <= 180) { "Invalid file name." }
        return name
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
