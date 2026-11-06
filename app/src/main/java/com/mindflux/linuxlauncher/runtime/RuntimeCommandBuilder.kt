package com.mindflux.linuxlauncher.runtime

import java.io.File

/** Pure command construction kept separate from Android UI for deterministic testing. */
object RuntimeCommandBuilder {
    private val safeId = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,39}")

    fun rootfsDirectory(filesDir: File, distroId: String): File {
        require(safeId.matches(distroId)) { "Invalid distribution identifier" }
        val base = File(filesDir, "linux-containers").canonicalFile
        val rootfs = File(base, "$distroId/rootfs").canonicalFile
        require(rootfs.path.startsWith(base.path + File.separator)) { "Container path escapes app storage" }
        return rootfs
    }

    private fun baseCommand(proot: File, rootfs: File): List<String> {
        require(proot.isFile && proot.canExecute()) { "PRoot engine is not installed for this device" }
        require(rootfs.isDirectory && (File(rootfs, "bin").isDirectory || File(rootfs, "usr/bin").isDirectory)) { "Linux root filesystem is not installed" }
        return listOf(proot.absolutePath, "--link2symlink", "-0", "-r", rootfs.absolutePath,
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-w", "/root",
            "/usr/bin/env", "-i", "HOME=/root", "USER=root", "TERM=xterm-256color", "TMPDIR=/tmp",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
    }
    fun buildInteractiveShell(proot: File, rootfs: File): List<String> = baseCommand(proot, rootfs) + listOf("/bin/sh", "-l")
    fun buildCommand(proot: File, rootfs: File, command: String): List<String> {
        require(command.isNotBlank()) { "Linux command cannot be empty" }
        return baseCommand(proot, rootfs) + listOf("/bin/sh", "-lc", command)
    }

    fun supportedAndroidAbi(supportedAbis: List<String>): String? =
        supportedAbis.firstOrNull { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
}
