package com.mindflux.linuxlauncher.runtime

import android.content.Context
import java.io.File

/** Finds only the app-bundled native runtime; never executes an arbitrary downloaded file. */
class RuntimeBinaryLocator(context: Context) {
    private val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)

    fun prootExecutable(): File? = File(nativeDirectory, "libproot.so")
        .takeIf { it.isFile && it.canExecute() }

    fun isAvailable(): Boolean = prootExecutable() != null
}
