package app.nova.desktop.graphics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class GraphicsCapabilities(
    val supportedAbis: List<String>,
    val openGlesVersion: String,
    val vulkanSupported: Boolean,
    val vulkanHardwareLevel: Int?,
    val vulkanHardwareVersion: Int?,
    val note: String
)

/** Detects host APIs only; this does not implement guest Vulkan, Direct3D or DXVK translation. */
object GraphicsCapabilitiesProbe {
    fun inspect(context: Context): GraphicsCapabilities {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = context.packageManager
        val features = pm.systemAvailableFeatures.orEmpty()
        val level = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }?.version
        val version = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }?.version
        return GraphicsCapabilities(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            openGlesVersion = manager.deviceConfigurationInfo?.glEsVersion ?: "Unknown",
            vulkanSupported = Build.VERSION.SDK_INT >= 24 && pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL),
            vulkanHardwareLevel = level,
            vulkanHardwareVersion = version,
            note = "Host capability detection only. Guest GPU/API translation is not integrated."
        )
    }
}
