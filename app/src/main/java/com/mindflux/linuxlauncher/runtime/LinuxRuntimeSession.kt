package com.mindflux.linuxlauncher.runtime

import java.io.File

/** Starts a PRoot-backed shell after validating both the bundled engine and rootfs. */
class LinuxRuntimeSession(
    private val proot: File,
    private val rootfs: File
) {
    fun startShell(): Process = start(RuntimeCommandBuilder.buildInteractiveShell(proot, rootfs))
    fun startCommand(command: String): Process = start(RuntimeCommandBuilder.buildCommand(proot, rootfs, command))
    private fun start(command: List<String>): Process = ProcessBuilder(command).directory(rootfs).redirectErrorStream(true).start()
}
