package com.mindflux.linuxlauncher.runtime

import java.io.File
import android.os.Process as AndroidProcess

/** Starts a PRoot-backed shell after validating both the bundled engine and rootfs. */
class LinuxRuntimeSession(
    private val proot: File,
    private val rootfs: File,
    private val threadPriority: Int = AndroidProcess.THREAD_PRIORITY_DEFAULT
) {
    fun startShell(): Process = start(RuntimeCommandBuilder.buildInteractiveShell(proot, rootfs))
    fun startCommand(command: String): Process = start(RuntimeCommandBuilder.buildCommand(proot, rootfs, command))
    private fun start(command: List<String>): Process {
        val tid = AndroidProcess.myTid()
        val previous = runCatching { AndroidProcess.getThreadPriority(tid) }.getOrDefault(AndroidProcess.THREAD_PRIORITY_DEFAULT)
        return try {
            runCatching { AndroidProcess.setThreadPriority(tid, threadPriority) }
            ProcessBuilder(command).directory(rootfs).redirectErrorStream(true).start()
        } finally {
            runCatching { AndroidProcess.setThreadPriority(tid, previous) }
        }
    }
}
