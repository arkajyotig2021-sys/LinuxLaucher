package com.mindflux.linuxlauncher

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class RuntimeBinarySmokeTest {
    @Test
    fun bundledProotExecutableStartsAndReportsVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(nativeDir, "libproot.so")
        val loader = File(nativeDir, "libproot-loader.so")

        assertTrue("Bundled PRoot executable is missing or not executable: $proot", proot.isFile && proot.canExecute())
        assertTrue("Bundled PRoot loader is missing or not executable: $loader", loader.isFile && loader.canExecute())

        val process = ProcessBuilder(proot.absolutePath, "--version")
            .directory(context.filesDir)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            throw AssertionError("Bundled PRoot --version did not exit within 30 seconds")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.exitValue()

        assertEquals("PRoot did not start cleanly: $output", 0, exitCode)
        assertTrue("PRoot version output was unexpected: $output", output.contains("proot", ignoreCase = true))
    }
}
