package pt.ineeve.bikefitapp.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FrameSampler.
 */
class FrameSamplerTest {

    private lateinit var sampler: FrameSampler

    @Before
    fun setUp() {
        sampler = FrameSampler(10f) // 10 FPS = 100ms interval
    }

    @Test
    fun `first frame is always processed`() {
        assertTrue(sampler.shouldProcessFrame(0L))
    }

    @Test
    fun `frame within interval is dropped`() {
        assertTrue(sampler.shouldProcessFrame(0L))
        assertFalse(sampler.shouldProcessFrame(50L)) // 50ms < 100ms interval
    }

    @Test
    fun `frame at exact interval is processed`() {
        assertTrue(sampler.shouldProcessFrame(0L))
        assertTrue(sampler.shouldProcessFrame(100L)) // Exactly at 100ms
    }

    @Test
    fun `frame after interval is processed`() {
        assertTrue(sampler.shouldProcessFrame(0L))
        assertTrue(sampler.shouldProcessFrame(150L)) // 150ms > 100ms interval
    }

    @Test
    fun `multiple frames respect interval from last accepted`() {
        assertTrue(sampler.shouldProcessFrame(0L))     // Accepted at 0
        assertFalse(sampler.shouldProcessFrame(50L))   // Dropped
        assertFalse(sampler.shouldProcessFrame(80L))   // Dropped
        assertTrue(sampler.shouldProcessFrame(100L))   // Accepted at 100
        assertFalse(sampler.shouldProcessFrame(150L))  // Dropped (only 50ms since 100)
        assertTrue(sampler.shouldProcessFrame(200L))   // Accepted at 200
    }

    @Test
    fun `setTargetFps updates interval`() {
        sampler.setTargetFps(5f) // 5 FPS = 200ms interval
        
        assertTrue(sampler.shouldProcessFrame(0L))
        assertFalse(sampler.shouldProcessFrame(100L))  // 100ms < 200ms
        assertTrue(sampler.shouldProcessFrame(200L))   // 200ms = interval
    }

    @Test
    fun `targetFps is clamped to minimum`() {
        sampler.setTargetFps(0.1f) // Below minimum
        assertEquals(FrameSampler.MIN_FPS, sampler.targetFps)
    }

    @Test
    fun `targetFps is clamped to maximum`() {
        sampler.setTargetFps(120f) // Above maximum
        assertEquals(FrameSampler.MAX_FPS, sampler.targetFps)
    }

    @Test
    fun `reset clears last frame timestamp`() {
        assertTrue(sampler.shouldProcessFrame(0L))
        assertFalse(sampler.shouldProcessFrame(50L))
        
        sampler.reset()
        
        // After reset, frame at 50ms should be accepted (treated as first frame)
        assertTrue(sampler.shouldProcessFrame(50L))
    }

    @Test
    fun `default FPS is 10`() {
        val defaultSampler = FrameSampler()
        assertEquals(FrameSampler.DEFAULT_TARGET_FPS, defaultSampler.targetFps)
    }

    @Test
    fun `high FPS means more frames processed`() {
        sampler.setTargetFps(30f) // 30 FPS = ~33ms interval
        
        assertTrue(sampler.shouldProcessFrame(0L))
        assertFalse(sampler.shouldProcessFrame(20L))   // 20ms < 33ms
        assertTrue(sampler.shouldProcessFrame(33L))    // 33ms >= interval
    }
}
