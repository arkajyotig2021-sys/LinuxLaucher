package app.nova.desktop.graphics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build

data class GraphicsCapabilities(
    val supportedAbis: List<String>,
    val openGlesVersion: String,
    val vulkanSupported: Boolean,
    val vulkanHardwareLevel: Int?,
    val vulkanHardwareVersion: Int?,
    val renderer: String,
    val vendor: String,
    val driverVersion: String,
    val isMali: Boolean,
    val guestTranslationAvailable: Boolean,
    val note: String
)

/** Host GPU/API probe only. It does not translate guest Vulkan or Direct3D calls. */
object GraphicsCapabilitiesProbe {
    fun inspect(context: Context): GraphicsCapabilities {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager
        val features = pm.systemAvailableFeatures.orEmpty()
        val level = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }?.version
        val version = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }?.version
        val gl = probeGl()
        val renderer = gl[0]
        val vendor = gl[1]
        return GraphicsCapabilities(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            openGlesVersion = manager.deviceConfigurationInfo?.glEsVersion ?: "Unknown",
            vulkanSupported = Build.VERSION.SDK_INT >= 24 && pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL),
            vulkanHardwareLevel = level,
            vulkanHardwareVersion = version,
            renderer = renderer,
            vendor = vendor,
            driverVersion = gl[2],
            isMali = renderer.contains("Mali", ignoreCase = true) || vendor.contains("ARM", ignoreCase = true),
            guestTranslationAvailable = false,
            note = "Host GPU detection only. No validated Mali guest Vulkan/Direct3D translation backend is bundled."
        )
    }

    private fun probeGl(): Array<String> {
        var display = EGL14.EGL_NO_DISPLAY
        var surface = EGL14.EGL_NO_SURFACE
        var context = EGL14.EGL_NO_CONTEXT
        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val versions = IntArray(2)
            check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, versions, 0, versions, 1))
            val attributes = intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0)
            val config = configs[0] ?: error("No EGL config.")
            val contextAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
            check(context != EGL14.EGL_NO_CONTEXT)
            val surfaceAttrs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttrs, 0)
            check(surface != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
            arrayOf(
                GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown",
                GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown",
                GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"
            )
        } catch (_: Throwable) {
            arrayOf("Unavailable", "Unavailable", "Unavailable")
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
        }
    }
}
