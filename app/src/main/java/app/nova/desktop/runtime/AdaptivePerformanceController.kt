package app.nova.desktop.runtime

data class AdaptivePerformancePolicy(
    val effectiveProfile: String,
    val maxBackgroundWorkers: Int,
    val previewMaxDimensionPx: Int,
    val previewCacheBudgetMb: Int,
    val allowParallelRuntimeDownloads: Boolean,
    val reduceNonEssentialWork: Boolean,
    val reason: String
)

object AdaptivePerformanceController {
    fun evaluate(requestedProfile: String, availableRamMb: Int, thermalStatus: String, batterySaver: Boolean = false): AdaptivePerformancePolicy {
        val thermal = thermalStatus.lowercase()
        val critical = thermal in setOf("severe", "critical", "emergency", "shutdown")
        val warm = thermal in setOf("moderate", "light")
        val lowMemory = availableRamMb in 1..599
        val constrained = availableRamMb in 600..999
        val effective = when {
            critical || batterySaver -> "Battery Saver"
            lowMemory || warm -> "Balanced"
            requestedProfile == "Battery Saver" -> "Battery Saver"
            requestedProfile == "Balanced" -> "Balanced"
            else -> "Smooth"
        }
        val workers = if (effective == "Smooth" && availableRamMb >= 1500) 2 else 1
        val preview = when {
            lowMemory || effective == "Battery Saver" -> 800
            constrained || effective == "Balanced" -> 1200
            else -> 1600
        }
        val cache = when (effective) { "Smooth" -> 96; "Balanced" -> 48; else -> 24 }
        val reason = when {
            critical -> "Thermal protection is active."
            batterySaver -> "Android Battery Saver is active."
            lowMemory -> "Available memory is low."
            warm -> "Device temperature is elevated."
            constrained -> "Memory headroom is limited."
            else -> "Requested profile can be maintained."
        }
        return AdaptivePerformancePolicy(effective, workers, preview, cache, effective == "Smooth" && workers > 1, effective != "Smooth", reason)
    }
}
