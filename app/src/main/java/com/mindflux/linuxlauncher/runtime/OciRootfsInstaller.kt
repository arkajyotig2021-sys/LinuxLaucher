package com.mindflux.linuxlauncher.runtime

import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest


/** Validates a server response before appending bytes to an interrupted OCI layer. */
internal object DownloadResumePolicy {
    fun shouldAppend(previousBytes: Long, statusCode: Int, contentRange: String?): Boolean {
        if (previousBytes <= 0L || statusCode != HttpURLConnection.HTTP_PARTIAL) return false
        val range = contentRange ?: error("Registry omitted Content-Range for resumed download")
        require(range.startsWith("bytes $previousBytes-") ) { "Registry returned an unexpected byte range" }
        return true
    }
}

/** Registry-aware OCI rootfs installer. Linux shares Android's kernel; this is not a VM. */
private data class OciImageReference(val registry: String, val repository: String, val tag: String)

class OciRootfsInstaller(private val filesDir: File) {
    data class Progress(val message: String, val downloaded: Long = 0, val total: Long = -1)

    fun hasPartialDownloads(distroId: String): Boolean {
        require(distroId.matches(Regex("[a-z0-9][a-z0-9._-]{0,39}"))) { "Invalid distro ID" }
        val cache = File(filesDir, "linux-cache/$distroId")
        return cache.listFiles()?.any { it.isFile && it.name.endsWith(".part") && it.length() > 0L } == true
    }

    fun clearPartialDownloads(distroId: String) {
        require(distroId.matches(Regex("[a-z0-9][a-z0-9._-]{0,39}"))) { "Invalid distro ID" }
        File(filesDir, "linux-cache/$distroId").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".part") }
            ?.forEach { it.delete() }
    }

    suspend fun install(
        image: String,
        distroId: String,
        resumeInterrupted: Boolean = true,
        onProgress: (Progress) -> Unit = {}
    ) =
        withContext(Dispatchers.IO) {
            require(distroId.matches(Regex("[a-z0-9][a-z0-9._-]{0,39}"))) { "Invalid distro ID" }
            val arch = dockerArch() ?: error("Unsupported CPU architecture")
            val root = File(filesDir, "linux-containers/" + distroId + "/rootfs").canonicalFile
            val base = File(filesDir, "linux-containers").canonicalFile
            require(root.path.startsWith(base.path + File.separator))
            val work = File(filesDir, "linux-cache/" + distroId).apply { mkdirs() }
            if (!resumeInterrupted) clearPartialDownloads(distroId)
            val staging = File(root.parentFile, "rootfs.installing")
            if (staging.exists()) staging.deleteRecursively()
            check(staging.mkdirs()) { "Cannot create installation directory" }
            try {
                val reference = parseImage(image)
                val repo = reference.repository
                val registryBase = "https://" + reference.registry + "/v2/"
                onProgress(Progress("Contacting " + reference.registry))
                val token = registryToken(reference.registry, repo)
                val index = requestJson(registryBase + repo + "/manifests/" + reference.tag, token, manifestAccept)
                val manifests = index.optJSONArray("manifests")
                val selected = if (manifests == null) index else {
                    val descriptor = (0 until manifests.length()).map { manifests.getJSONObject(it) }
                        .firstOrNull {
                            val p = it.optJSONObject("platform") ?: JSONObject()
                            p.optString("os") == "linux" && p.optString("architecture") == arch &&
                                (arch != "arm64" || p.optString("variant").let { v -> v.isBlank() || v == "v8" })
                        } ?: error("No " + arch + " Linux image is available for " + image)
                    requestJson(registryBase + repo + "/manifests/" + descriptor.getString("digest"), token, manifestAccept)
                }
                val layers = selected.getJSONArray("layers")
                val descriptors = (0 until layers.length()).map { layers.getJSONObject(it) }
                val archives = descriptors.map { layer ->
                    val digest = layer.getString("digest")
                    require(digest.matches(Regex("sha256:[a-f0-9]{64}"))) { "Invalid layer digest" }
                    File(work, digest.removePrefix("sha256:") + ".layer")
                }
                val expectedTotal = descriptors.sumOf { it.optLong("size", -1L) }
                    .takeIf { it > 0L && descriptors.all { d -> d.optLong("size", -1L) > 0L } } ?: -1L
                val layerProgress = LongArray(descriptors.size)
                val progressLock = Any()
                val downloadSlots = Semaphore(4)
                coroutineScope {
                    descriptors.mapIndexed { index, layer ->
                        async(Dispatchers.IO) {
                            downloadSlots.withPermit {
                                val digest = layer.getString("digest").removePrefix("sha256:")
                                val archive = archives[index]
                                if (!archive.isFile || sha256(archive) != digest) {
                                    archive.delete()
                                    onProgress(Progress("Downloading layers · up to 2 connections"))
                                    download(registryBase + repo + "/blobs/sha256:" + digest, token, archive) { got, _ ->
                                        val aggregate = synchronized(progressLock) {
                                            layerProgress[index] = got
                                            layerProgress.sum()
                                        }
                                        onProgress(Progress("Downloading layer ${index + 1}/${descriptors.size} · 4-connection mode", aggregate, expectedTotal))
                                    }
                                    check(sha256(archive) == digest) { "Layer checksum failed" }
                                } else {
                                    synchronized(progressLock) { layerProgress[index] = archive.length() }
                                }
                            }
                        }
                    }.awaitAll()
                }
                // OCI layers are applied in manifest order even though their transfers overlap.
                descriptors.forEachIndexed { index, layer ->
                    onProgress(Progress("Extracting layer ${index + 1}/${descriptors.size}"))
                    extractLayer(archives[index], staging, layer.optString("mediaType"))
                }
                check(File(staging, "bin").exists() || File(staging, "usr/bin").exists()) {
                    "Downloaded image is not a valid Linux root filesystem"
                }
                File(staging, "etc/resolv.conf").apply {
                    parentFile?.mkdirs()
                    writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
                }
                if (root.exists()) check(root.deleteRecursively()) { "Could not replace existing root filesystem" }
                check(staging.renameTo(root)) { "Could not finalize Linux installation" }
                onProgress(Progress("Linux system installed"))
            } catch (t: Throwable) {
                staging.deleteRecursively()
                throw t
            }
        }

    private fun parseImage(image: String): OciImageReference {
        require(image.isNotBlank() && !image.any { it.isWhitespace() }) { "Enter an OCI image reference" }
        val tagSeparator = image.lastIndexOf(':')
        val slashSeparator = image.lastIndexOf('/')
        val tag = if (tagSeparator > slashSeparator) image.substring(tagSeparator + 1) else "latest"
        val name = if (tagSeparator > slashSeparator) image.substring(0, tagSeparator) else image
        val firstSlash = name.indexOf('/')
        val firstPart = if (firstSlash >= 0) name.substring(0, firstSlash) else ""
        val hasRegistry = firstSlash >= 0 && ('.' in firstPart || ':' in firstPart || firstPart == "localhost")
        val detectedRegistry = if (hasRegistry) firstPart else "registry-1.docker.io"
        val registry = if (detectedRegistry == "docker.io") "registry-1.docker.io" else detectedRegistry
        val rawRepo = if (hasRegistry) name.substring(firstSlash + 1) else name
        val repo = if (!hasRegistry && '/' !in rawRepo) "library/" + rawRepo else rawRepo
        require(registry.matches(Regex("[a-zA-Z0-9.-]+(:[0-9]{1,5})?"))) { "Invalid OCI registry host" }
        require(repo.matches(Regex("[a-zA-Z0-9._/-]+")) && !repo.contains("..")) { "Invalid OCI repository path" }
        require(tag.matches(Regex("[a-zA-Z0-9._-]{1,128}"))) { "Invalid OCI image tag" }
        return OciImageReference(registry, repo, tag)
    }

    private fun dockerArch(): String? = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "arm64"
        Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "arm"
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "amd64"
        Build.SUPPORTED_ABIS.any { it == "x86" } -> "386"
        else -> null
    }

    private fun registryToken(registry: String, repo: String): String? {
        val c = URL("https://" + registry + "/v2/").openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 20000
        try {
            val status = c.responseCode
            if (status == 200) return null
            if (status != 401) error("Registry authentication check failed (HTTP " + status + ")")
            val challenge = c.getHeaderField("WWW-Authenticate") ?: error("Registry did not provide an authentication challenge")
            require(challenge.startsWith("Bearer ", ignoreCase = true)) { "This OCI registry uses an unsupported authentication scheme" }
            val realm = Regex("realm=\"([^\"]+)\"").find(challenge)?.groupValues?.get(1) ?: error("Invalid registry challenge")
            val service = Regex("service=\"([^\"]+)\"").find(challenge)?.groupValues?.get(1)
            val scope = java.net.URLEncoder.encode("repository:" + repo + ":pull", "UTF-8")
            val separator = if ('?' in realm) "&" else "?"
            val tokenUrl = realm + separator + (service?.let { "service=" + java.net.URLEncoder.encode(it, "UTF-8") + "&" } ?: "") + "scope=" + scope
            val json = requestJson(tokenUrl, null, null)
            return json.optString("token").ifBlank { json.optString("access_token") }.ifBlank { error("Registry did not return a bearer token") }
        } finally { c.disconnect() }
    }

    private fun requestJson(url: String, token: String?, accept: String?): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 30000
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token)
        if (accept != null) c.setRequestProperty("Accept", accept)
        try {
            val status = c.responseCode
            if (status !in 200..299) error("Registry request failed (HTTP " + status + ")")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }

    private suspend fun download(url: String, token: String?, target: File, progress: (Long, Long) -> Unit) {
        val jobContext = currentCoroutineContext()
        val partial = File(target.parentFile, target.name + ".part")
        val previousBytes = partial.length()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 120000
        c.instanceFollowRedirects = true
        c.setRequestProperty("Accept-Encoding", "identity")
        c.setRequestProperty("Connection", "keep-alive")
        c.setRequestProperty("User-Agent", "LinuxLauncher-Android/0.3")
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token)
        if (previousBytes > 0L) c.setRequestProperty("Range", "bytes=" + previousBytes + "-")
        try {
            val status = c.responseCode
            if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_PARTIAL) {
                error("Layer download failed (HTTP " + status + ")")
            }
            val append = DownloadResumePolicy.shouldAppend(previousBytes, status, c.getHeaderField("Content-Range"))
            if (status == HttpURLConnection.HTTP_PARTIAL) {
                require(append) { "Registry returned a partial response without a valid resume offset" }
            }
            if (previousBytes > 0L && !append) partial.delete()
            val initialBytes = if (append) previousBytes else 0L
            val contentLength = c.contentLengthLong
            val total = if (contentLength > 0L) contentLength + initialBytes else -1L
            BufferedInputStream(c.inputStream, 1024 * 1024).use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var done = initialBytes
                    progress(done, total)
                    while (true) {
                        jobContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        done += count
                        progress(done, total)
                    }
                    output.fd.sync()
                }
            }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Could not finalize downloaded OCI layer" }
        } finally { c.disconnect() }
    }

    private suspend fun sha256(file: File): String {
        val jobContext = currentCoroutineContext()
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val b = ByteArray(128 * 1024)
            while (true) {
                jobContext.ensureActive()
                val n = input.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun extractLayer(archive: File, destination: File, mediaType: String) {
        val jobContext = currentCoroutineContext()
        require(!mediaType.contains("zstd", ignoreCase = true)) {
            "This OCI image uses zstd-compressed layers; this build currently supports gzip and uncompressed tar layers"
        }
        FileInputStream(archive).use { file ->
            val buffered = BufferedInputStream(file)
            buffered.mark(4)
            val first = buffered.read()
            val second = buffered.read()
            buffered.reset()
            val isGzip = mediaType.contains("gzip", ignoreCase = true) || (first == 0x1f && second == 0x8b)
            val decoded = if (isGzip) GzipCompressorInputStream(buffered) else buffered
            TarArchiveInputStream(decoded).use { tar ->
                while (true) {
                    jobContext.ensureActive()
                    val entry = tar.nextTarEntry ?: break
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    if (name.isBlank()) continue
                    val out = File(destination, name).canonicalFile
                    require(out.path == destination.canonicalPath ||
                        out.path.startsWith(destination.canonicalPath + File.separator)) { "Unsafe path in Linux image" }
                    val leaf = out.name
                    if (leaf == ".wh..wh..opq") {
                        out.parentFile?.listFiles()?.forEach { it.deleteRecursively() }
                        continue
                    }
                    if (leaf.startsWith(".wh.")) {
                        File(out.parentFile, leaf.removePrefix(".wh.")).deleteRecursively()
                        continue
                    }
                    when {
                        entry.isDirectory -> out.mkdirs()
                        entry.isSymbolicLink -> {
                            out.parentFile?.mkdirs()
                            if (out.exists() || out.isSymlink()) out.delete()
                            if (!entry.linkName.contains("\u0000")) {
                                val target = if (entry.linkName.startsWith("/")) {
                                    relativePath(out.parentFile ?: destination, File(destination, entry.linkName.removePrefix("/")))
                                } else entry.linkName
                                Os.symlink(target, out.absolutePath)
                            }
                        }
                        entry.isLink -> {
                            val linkName = entry.linkName.removePrefix("./").trimStart('/')
                            val linkTarget = File(destination, linkName).canonicalFile
                            require(linkTarget.path.startsWith(destination.canonicalPath + File.separator) && linkTarget.isFile) {
                                "Invalid hard-link target in Linux image"
                            }
                            out.parentFile?.mkdirs()
                            if (out.exists()) out.delete()
                            try {
                                Os.link(linkTarget.absolutePath, out.absolutePath)
                            } catch (_: Throwable) {
                                FileInputStream(linkTarget).use { input ->
                                    FileOutputStream(out).use { output -> input.copyTo(output) }
                                }
                            }
                            out.setReadable(entry.mode and 0b100100100 != 0, false)
                            out.setWritable(entry.mode and 0b010010010 != 0, false)
                            out.setExecutable(entry.mode and 0b001001001 != 0, false)
                        }
                        entry.isFile -> {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { output -> tar.copyTo(output) }
                            out.setReadable(entry.mode and 0b100100100 != 0, false)
                            out.setWritable(entry.mode and 0b010010010 != 0, false)
                            out.setExecutable(entry.mode and 0b001001001 != 0, false)
                        }
                    }
                }
            }
        }
    }

    private fun relativePath(from: File, to: File): String {
        val a = from.canonicalPath.split(File.separatorChar).filter { it.isNotEmpty() }
        val b = to.canonicalPath.split(File.separatorChar).filter { it.isNotEmpty() }
        var common = 0
        while (common < a.size && common < b.size && a[common] == b[common]) common++
        val parts = ArrayList<String>()
        repeat(a.size - common) { parts.add("..") }
        parts.addAll(b.drop(common))
        return if (parts.isEmpty()) "." else parts.joinToString("/")
    }

    private fun File.isSymlink(): Boolean = try { Os.readlink(absolutePath); true } catch (_: Exception) { false }

    companion object {
        private const val manifestAccept =
            "application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.docker.distribution.manifest.v2+json"
    }
}
