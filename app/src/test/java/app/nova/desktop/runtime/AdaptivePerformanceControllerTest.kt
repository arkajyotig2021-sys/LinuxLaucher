package app.nova.desktop.runtime

import org.junit.Assert.*
import org.junit.Test

class AdaptivePerformanceControllerTest {
    @Test fun lowMemoryForcesBalanced() {
        val policy = AdaptivePerformanceController.evaluate("Smooth", 450, "Normal")
        assertEquals("Balanced", policy.effectiveProfile)
        assertEquals(800, policy.previewMaxDimensionPx)
        assertFalse(policy.allowParallelRuntimeDownloads)
    }
    @Test fun severeThermalForcesBatterySaver() {
        val policy = AdaptivePerformanceController.evaluate("Smooth", 2500, "Severe")
        assertEquals("Battery Saver", policy.effectiveProfile)
        assertTrue(policy.reduceNonEssentialWork)
    }
    @Test fun healthyDeviceCanUseSmoothMode() {
        val policy = AdaptivePerformanceController.evaluate("Smooth", 2200, "Normal")
        assertEquals("Smooth", policy.effectiveProfile)
        assertTrue(policy.allowParallelRuntimeDownloads)
    }
}
