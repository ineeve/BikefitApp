package pt.ineeve.bikefitapp.biomechanics

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/**
 * Unit tests for CrankAngleFilter - angle smoothing and outlier rejection.
 */
class CrankAngleFilterTest {
    
    private lateinit var filterState: CrankAngleFilter.FilterState
    
    @BeforeEach
    fun setUp() {
        filterState = CrankAngleFilter.FilterState()
    }
    
    // ==================== Basic Filtering Tests ====================
    
    @Test
    fun `first measurement is always accepted`() {
        val result = CrankAngleFilter.filterAngle(90f, filterState)
        
        assertTrue(result.isValid)
        assertFalse(result.isOutlier)
        assertEquals(90f, result.angle, 0.01f)
        assertEquals(90f, filterState.smoothedAngle!!, 0.01f)
    }
    
    @Test
    fun `small angle change is accepted`() {
        CrankAngleFilter.filterAngle(90f, filterState)
        val result = CrankAngleFilter.filterAngle(95f, filterState)
        
        assertTrue(result.isValid)
        assertFalse(result.isOutlier)
        assertEquals(5f, result.angleDelta!!, 0.01f)
    }
    
    @Test
    fun `large angle change is rejected as outlier`() {
        val config = CrankAngleFilter.FilterConfig(maxAngleChangePerFrame = 30f)
        CrankAngleFilter.filterAngle(90f, filterState, config)
        
        val result = CrankAngleFilter.filterAngle(150f, filterState, config)  // 60° change
        
        assertFalse(result.isValid)
        assertTrue(result.isOutlier)
        assertEquals(60f, result.angleDelta!!, 0.01f)
    }
    
    @Test
    fun `exactly at threshold is accepted`() {
        val config = CrankAngleFilter.FilterConfig(maxAngleChangePerFrame = 30f)
        CrankAngleFilter.filterAngle(90f, filterState, config)
        
        val result = CrankAngleFilter.filterAngle(120f, filterState, config)  // Exactly 30°
        
        assertTrue(result.isValid)
        assertFalse(result.isOutlier)
    }
    
    // ==================== Smoothing Tests ====================
    
    @Test
    fun `exponential moving average smoothing works`() {
        val config = CrankAngleFilter.FilterConfig(smoothingFactor = 0.5f)
        
        // Start at 90°
        CrankAngleFilter.filterAngle(90f, filterState, config)
        assertEquals(90f, filterState.smoothedAngle!!, 0.01f)
        
        // Jump to 100° (within threshold)
        val result = CrankAngleFilter.filterAngle(100f, filterState, config)
        // smoothed = 0.5 * 100 + 0.5 * 90 = 95
        assertEquals(95f, result.angle!!, 0.01f)
    }
    
    @Test
    fun `smoothing factor controls responsiveness - low factor is smooth`() {
        val config = CrankAngleFilter.FilterConfig(smoothingFactor = 0.2f)
        
        CrankAngleFilter.filterAngle(90f, filterState, config)
        val result = CrankAngleFilter.filterAngle(100f, filterState, config)
        
        // smoothed = 0.2 * 100 + 0.8 * 90 = 20 + 72 = 92
        assertEquals(92f, result.angle!!, 0.01f)
    }
    
    @Test
    fun `smoothing factor controls responsiveness - high factor is responsive`() {
        val config = CrankAngleFilter.FilterConfig(smoothingFactor = 0.8f)
        
        CrankAngleFilter.filterAngle(90f, filterState, config)
        val result = CrankAngleFilter.filterAngle(100f, filterState, config)
        
        // smoothed = 0.8 * 100 + 0.2 * 90 = 80 + 18 = 98
        assertEquals(98f, result.angle!!, 0.01f)
    }
    
    // ==================== Angle Wrapping Tests ====================
    
    @Test
    fun `angle wrap at 0 to 360 boundary works correctly`() {
        val config = CrankAngleFilter.FilterConfig(maxAngleChangePerFrame = 30f)
        
        CrankAngleFilter.filterAngle(10f, filterState, config)
        // Should go from 10° to 350° = -20° delta (wrapping), which is within tolerance
        val result = CrankAngleFilter.filterAngle(350f, filterState, config)
        
        assertTrue(result.isValid)
        assertFalse(result.isOutlier)
        // Delta should be -20° (going backwards)
        assertEquals(-20f, result.angleDelta!!, 0.01f)
    }
    
    @Test
    fun `360 to 0 transition is handled correctly`() {
        val config = CrankAngleFilter.FilterConfig(maxAngleChangePerFrame = 30f)
        
        CrankAngleFilter.filterAngle(350f, filterState, config)
        val result = CrankAngleFilter.filterAngle(10f, filterState, config)
        
        assertTrue(result.isValid)
        assertFalse(result.isOutlier)
        // Delta should be +20° (going forward across boundary)
        assertEquals(20f, result.angleDelta!!, 0.01f)
    }
    
    @Test
    fun `large backward wrap is rejected`() {
        val config = CrankAngleFilter.FilterConfig(maxAngleChangePerFrame = 30f)
        
        CrankAngleFilter.filterAngle(10f, filterState, config)
        // Jump to 300° = -70° delta (too large, even with wrapping)
        val result = CrankAngleFilter.filterAngle(300f, filterState, config)
        
        assertTrue(result.isOutlier)
        assertEquals(-70f, result.angleDelta!!, 0.01f)
    }
    
    // ==================== State Management Tests ====================
    
    @Test
    fun `reset clears filter state`() {
        CrankAngleFilter.filterAngle(90f, filterState)
        CrankAngleFilter.filterAngle(95f, filterState)
        
        assertNotNull(filterState.lastValidAngle)
        assertNotNull(filterState.smoothedAngle)
        
        CrankAngleFilter.reset(filterState)
        
        assertNull(filterState.lastValidAngle)
        assertNull(filterState.smoothedAngle)
        assertEquals(0, filterState.framesSinceLastValid)
    }
    
    @Test
    fun `frame counter increments on outlier`() {
        val config = CrankAngleFilter.FilterConfig(maxAngleChangePerFrame = 30f)
        
        CrankAngleFilter.filterAngle(90f, filterState, config)
        assertEquals(0, filterState.framesSinceLastValid)
        
        // Outlier
        CrankAngleFilter.filterAngle(150f, filterState, config)
        assertEquals(1, filterState.framesSinceLastValid)
        
        // Another outlier
        CrankAngleFilter.filterAngle(200f, filterState, config)
        assertEquals(2, filterState.framesSinceLastValid)
    }
    
    @Test
    fun `frame counter resets on valid measurement`() {
        val config = CrankAngleFilter.FilterConfig(maxAngleChangePerFrame = 30f)
        
        CrankAngleFilter.filterAngle(90f, filterState, config)
        CrankAngleFilter.filterAngle(150f, filterState, config)  // Outlier
        assertEquals(1, filterState.framesSinceLastValid)
        
        CrankAngleFilter.filterAngle(110f, filterState, config)  // Valid (20° from 90°)
        assertEquals(0, filterState.framesSinceLastValid)
    }
    
    // ==================== Delta Calculation Tests ====================
    
    @Test
    fun `angle delta normal case`() {
        val delta = CrankAngleFilter.calculateAngleDelta(90f, 100f)
        assertEquals(10f, delta, 0.01f)
    }
    
    @Test
    fun `angle delta backward`() {
        val delta = CrankAngleFilter.calculateAngleDelta(100f, 90f)
        assertEquals(-10f, delta, 0.01f)
    }
    
    @Test
    fun `angle delta wrapping forward`() {
        val delta = CrankAngleFilter.calculateAngleDelta(350f, 10f)
        assertEquals(20f, delta, 0.01f)
    }
    
    @Test
    fun `angle delta wrapping backward`() {
        val delta = CrankAngleFilter.calculateAngleDelta(10f, 350f)
        assertEquals(-20f, delta, 0.01f)
    }
    
    @Test
    fun `angle delta 180 degrees`() {
        val delta = CrankAngleFilter.calculateAngleDelta(0f, 180f)
        assertEquals(180f, delta, 0.01f)
    }
    
    @Test
    fun `angle delta full rotation is zero`() {
        val delta = CrankAngleFilter.calculateAngleDelta(90f, 90f)
        assertEquals(0f, delta, 0.01f)
    }
}
