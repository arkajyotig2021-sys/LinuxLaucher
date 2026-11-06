package com.mindflux.linuxlauncher.runtime

import android.content.Context
import android.system.Os
import com.mindflux.linuxlauncher.runtime.RuntimeCommandBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Full rootfs backup/restore using tar.gz; the archive preserves Linux symlinks and modes. */
object LinuxBackupManager {
    suspend fun backup(context: Context, distroId: String, output: OutputStream, onProgress: (String) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val root = RuntimeCommandBuilder.rootfsDirectory(context.filesDir, distroId)
            require(root.isDirectory) { "Linux root filesystem is not installed" }
            val job = currentCoroutineContext()
            GzipCompressorOutputStream(output).use { gzip ->
                TarArchiveOutputStream(gzip).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    val files = root.walkTopDown().toList()
                    var count = 0
                    for (file in files) {
                        job.ensureActive()
                        val relative = file.absolutePath.removePrefix(root.absolutePath).trimStart(File.separatorChar).replace(File.separatorChar, '/')
                        if (relative.isBlank()) continue
                        val symlinkTarget = try { Os.readlink(file.absolutePath) } catch (_: Exception) { null }
                        when {
                            symlinkTarget != null -> {
                                val entry = TarArchiveEntry(relative, TarConstants.LF_SYMLINK)
                                entry.linkName = symlinkTarget
                                tar.putArchiveEntry(entry)
                                tar.closeArchiveEntry()
                            }
                            file.isDirectory -> {
                                val entry = TarArchiveEntry(file, "$relative/")
                                tar.putArchiveEntry(entry)
                                tar.closeArchiveEntry()
                            }
                            file.isFile -> {
                                val entry = TarArchiveEntry(file, relative)
                                entry.size = file.length()
                                tar.putArchiveEntry(entry)
                                FileInputStream(file).use { it.copyTo(tar) }
                                tar.closeArchiveEntry()
                            }
                        }
                        count++
                        if (count % 300 == 0) onProgress("Backed up $count filesystem entries…")
                    }
                    tar.finish()
                }
            }
            onProgress("Backup complete")
        }

    suspend fun restore(context: Context, distroId: String, input: InputStream, onProgress: (String) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            require(distroId.matches(Regex("[a-z0-9][a-z0-9._-]{0,39}"))) { "Invalid distribution identifier" }
            val root = RuntimeCommandBuilder.rootfsDirectory(context.filesDir, distroId)
            val staging = File(root.parentFile, "rootfs.restore").apply {
                if (exists()) deleteRecursively()
                check(mkdirs()) { "Cannot create restore staging directory" }
            }
            val job = currentCoroutineContext()
            try {
                GzipCompressorInputStream(input).use { gzip ->
                    TarArchiveInputStream(gzip).use { tar ->
                        var count = 0
                        while (true) {
                            job.ensureActive()
                            val entry = tar.nextTarEntry ?: break
                            val name = entry.name.replace('\\', '/').trimStart('/')
                            require(name.isNotBlank() && !name.split('/').any { it == ".." }) { "Unsafe path in backup archive" }
                            val out = File(staging, name).canonicalFile
                            require(out.path.startsWith(staging.canonicalPath + File.separator)) { "Backup path escapes restore directory" }
                            when {
                                entry.isDirectory -> out.mkdirs()
                                entry.isSymbolicLink -> {
                                    out.parentFile?.mkdirs()
                                    val target = entry.linkName
                                    if (!target.startsWith("/")) {
                                        val resolved = out.parentFile!!.toPath().resolve(target).normalize()
                                        require(resolved.startsWith(staging.toPath())) { "Unsafe symbolic link in backup" }
                                    }
                                    Os.symlink(target, out.absolutePath)
                                }
                                entry.isLink -> {
                                    val targetName = entry.linkName.removePrefix("./").trimStart('/')
                                    require(!targetName.split('/').any { it == ".." }) { "Unsafe hard link in backup" }
                                    val target = File(staging, targetName).canonicalFile
                                    require(target.path.startsWith(staging.canonicalPath + File.separator) && target.isFile) { "Missing hard-link target in backup" }
                                    out.parentFile?.mkdirs()
                                    FileInputStream(target).use { src -> FileOutputStream(out).use { src.copyTo(it) } }
                                }
                                entry.isFile -> {
                                    out.parentFile?.mkdirs()
                                    FileOutputStream(out).use { dest ->
                                        val buffer = ByteArray(64 * 1024)
                                        var remaining = entry.size
                                        while (remaining > 0) {
                                            job.ensureActive()
                                            val n = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                            if (n < 0) error("Backup archive ended unexpectedly")
                                            dest.write(buffer, 0, n)
                                            remaining -= n
                                        }
                                    }
                                    out.setReadable(entry.mode and 0b100100100 != 0, false)
                                    out.setWritable(entry.mode and 0b010010010 != 0, false)
                                    out.setExecutable(entry.mode and 0b001001001 != 0, false)
                                }
                            }
                            count++
                            if (count % 300 == 0) onProgress("Restored $count filesystem entries…")
                        }
                    }
                }
                check(File(staging, "bin").exists() || File(staging, "usr/bin").exists()) { "Backup does not contain a valid Linux root filesystem" }
                if (root.exists()) check(root.deleteRecursively()) { "Could not replace existing Linux filesystem" }
                check(staging.renameTo(root)) { "Could not finalize restored Linux filesystem" }
                onProgress("Restore complete")
            } catch (t: Throwable) {
                staging.deleteRecursively()
                throw t
            }
        }
}
