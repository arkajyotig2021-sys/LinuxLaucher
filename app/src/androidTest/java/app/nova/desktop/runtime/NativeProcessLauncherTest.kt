package app.nova.desktop.runtime

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeProcessLauncherTest {
    @Test
    fun launchesAndroidSystemElfFromAppProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pid = NativeProcessLauncher.start(
            executable = "/system/bin/sh",
            arguments = arrayOf("sh", "-c", "exit 0"),
            environment = arrayOf("PATH=/system/bin:/system/xbin"),
            workingDirectory = context.filesDir.absolutePath
        )
        assertTrue("Native process launch failed with errno " + pid, pid > 0)

        var exitCode = NativeProcessLauncher.STILL_RUNNING
        val deadline = System.currentTimeMillis() + 5000
        while (exitCode == NativeProcessLauncher.STILL_RUNNING && System.currentTimeMillis() < deadline) {
            exitCode = NativeProcessLauncher.nativePoll(pid)
            Thread.sleep(25)
        }
        assertEquals("Process did not exit successfully", 0, exitCode)
    }
}
