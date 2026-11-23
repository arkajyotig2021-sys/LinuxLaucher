package app.nova.desktop.runtime

import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

/**
 * Safe extractor for a previously SHA-256-verified .tar.gz root filesystem.
 * Device nodes and FIFOs are intentionally skipped; Android apps cannot create them.
 * Guest absolute symlinks are rewritten as rootfs-relative links.
 */
class LinuxRootfsInstaller {
    fun installVerifiedTarGz(archive: File, expectedSha256: String, destination: File): File {
        require(archive.isFile) { "Rootfs archive is missing." }
        require(expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) { "Pinned SHA-256 is required." }
        require(sha256(archive).equals(expectedSha256, ignoreCase = true)) { "Rootfs checksum verification failed." }

        val parent = destination.absoluteFile.parentFile ?: error("Invalid rootfs destination.")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create runtime directory.")
        val staging = File(parent, destination.name + ".installing")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Cannot create staging directory.")
        val rootCanonical = staging.canonicalFile

        try {
            TarArchiveInputStream(GZIPInputStream(FileInputStream(archive))).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val raw = entry.name.replace('\\', '/')
                    require(!raw.startsWith("/") && raw.split('/').none { it == ".." }) { "Unsafe path in rootfs archive." }
                    val relative = raw.trimStart('.', '/').trimEnd('/')
                    if (relative.isEmpty()) continue
                    val target = File(staging, relative)
                    val targetParent = target.parentFile ?: throw IOException("Invalid archive path.")
                    if (!targetParent.exists() && !targetParent.mkdirs()) throw IOException("Cannot create rootfs directory.")
                    require(isInside(rootCanonical, targetParent.canonicalFile)) { "Archive path escapes rootfs." }

                    when {
                        entry.isDirectory -> {
                            if (!target.exists() && !target.mkdirs()) throw IOException("Cannot create directory.")
                            target.setExecutable(true, false)
                        }
                        entry.isSymbolicLink -> {
                            val link = entry.linkName.replace('\\', '/')
                            val linkTarget = if (link.startsWith("/")) File(staging, link.removePrefix("/")) else File(targetParent, link)
                            require(isInside(rootCanonical, linkTarget.canonicalFile)) { "Symbolic link escapes rootfs." }
                            val relativeLink = relativePath(targetParent, linkTarget)
                            if (target.exists()) target.delete()
                            Os.symlink(relativeLink, target.absolutePath)
                        }
                        entry.isLink -> {
                            val link = File(staging, entry.linkName.removePrefix("/"))
                            require(isInside(rootCanonical, link.canonicalFile) && link.isFile) { "Invalid rootfs hard link." }
                            if (target.exists()) target.delete()
                            Os.link(link.absolutePath, target.absolutePath)
                        }
                        entry.isFile -> {
                            FileOutputStream(target).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = tar.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                            target.setExecutable((entry.mode and 0b001001001) != 0, false)
                            target.setReadable((entry.mode and 0b100100100) != 0, false)
                            target.setWritable((entry.mode and 0b010010010) != 0, false)
                        }
                        else -> Unit
                    }
                }
            }
            val marker = File(staging, ".nova-rootfs")
            marker.writeText("verified-sha256=" + expectedSha256.lowercase())
            destination.deleteRecursively()
            if (!staging.renameTo(destination)) throw IOException("Could not atomically activate rootfs.")
            return destination
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun isInside(root: File, candidate: File): Boolean {
        val base = root.canonicalPath
        val path = candidate.canonicalPath
        return path == base || path.startsWith(base + File.separator)
    }

    private fun relativePath(from: File, to: File): String {
        val fromParts = from.canonicalPath.split(File.separatorChar).filter { it.isNotEmpty() }
        val toParts = to.canonicalPath.split(File.separatorChar).filter { it.isNotEmpty() }
        var common = 0
        while (common < minOf(fromParts.size, toParts.size) && fromParts[common] == toParts[common]) common++
        val up = List(fromParts.size - common) { ".." }
        val down = toParts.drop(common)
        return (up + down).joinToString(File.separator).ifEmpty { "." }
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
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
