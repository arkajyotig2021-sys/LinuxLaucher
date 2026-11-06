package com.mindflux.linuxlauncher.runtime

import android.content.Context
import java.io.File

/** Resolves only PRoot executables shipped inside this APK's native library directory. */
class RuntimeBinaryLocator(context: Context) {
    private val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)

    fun prootExecutable(): File? = File(nativeDirectory, "libproot.so")
        .takeIf { it.isFile && it.canExecute() }

    fun loaderAvailable(): Boolean = File(nativeDirectory, "libproot-loader.so").isFile

    fun isAvailable(): Boolean = prootExecutable() != null && loaderAvailable()
}
