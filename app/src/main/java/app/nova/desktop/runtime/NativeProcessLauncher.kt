package app.nova.desktop.runtime

import android.os.Build

/**
 * ARM64 process-launch primitive for future native guest runtimes.
 *
 * This does not itself provide PRoot, Wine, Box64, a Linux rootfs or a GUI.
 * The executable must be a compatible Android/Bionic ARM64 ELF. On Android 10+
 * the launcher stages the validated executable into a memfd before execveat;
 * this path still requires real-device SELinux/kernel verification.
 */
object NativeProcessLauncher {
    const val STILL_RUNNING = -2147483647

    init {
        System.loadLibrary("novaexec")
    }

    /**
     * @param executable absolute path to a compatible ARM64 ELF
     * @param arguments argv including argv[0]
     * @param environment complete environment entries, e.g. "PATH=/system/bin"
     * @param workingDirectory existing directory
     * @return child PID, or a negative errno value
     */
    @JvmStatic
    fun start(executable: String, arguments: Array<String>, environment: Array<String>, workingDirectory: String): Int =
        nativeStart(executable, arguments, environment, workingDirectory, Build.VERSION.SDK_INT)

    @JvmStatic external fun nativeStart(
        executable: String,
        arguments: Array<String>,
        environment: Array<String>,
        workingDirectory: String,
        sdkInt: Int
    ): Int

    @JvmStatic external fun nativePoll(pid: Int): Int
    @JvmStatic external fun nativeStop(pid: Int): Int
}
