package com.mindflux.linuxlauncher.runtime

import java.io.File

/** Starts a PRoot-backed shell after validating both the bundled engine and rootfs. */
class LinuxRuntimeSession(
    private val proot: File,
    private val rootfs: File
) {
    fun startShell(): Process {
        val command = RuntimeCommandBuilder.buildInteractiveShell(proot, rootfs)
        return ProcessBuilder(command)
            .directory(rootfs)
            .redirectErrorStream(true)
            .start()
    }
}
