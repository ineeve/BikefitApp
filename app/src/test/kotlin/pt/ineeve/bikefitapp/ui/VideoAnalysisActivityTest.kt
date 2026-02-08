package pt.ineeve.bikefitapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for VideoAnalysisActivity constants.
 */
class VideoAnalysisActivityTest {

    @Test
    fun `target sampling fps is 60`() {
        // Reflection to access the private constant
        val field = VideoAnalysisActivity::class.java.getDeclaredField("TARGET_SAMPLING_FPS")
        field.isAccessible = true
        val targetFps = field.get(null) as Float
        
        assertEquals(60f, targetFps, 0.01f)
    }

    @Test
    fun `target interval ms is correct for 60fps`() {
        // 60 fps = 1000ms / 60 = 16.67ms per frame
        val field = VideoAnalysisActivity::class.java.getDeclaredField("TARGET_INTERVAL_MS")
        field.isAccessible = true
        val intervalMs = field.get(null) as Float
        
        assertEquals(16.67f, intervalMs, 0.01f)
    }

    @Test
    fun `target interval micros is correct for 60fps`() {
        // 16.67ms = 16666 or 16667 microseconds
        val field = VideoAnalysisActivity::class.java.getDeclaredField("TARGET_INTERVAL_MICROS")
        field.isAccessible = true
        val intervalMicros = field.get(null) as Long
        
        // Allow for rounding: 16666 or 16667 microseconds
        assert(intervalMicros == 16666L || intervalMicros == 16667L) {
            "Expected 16666 or 16667 microseconds, but got $intervalMicros"
        }
    }
}
