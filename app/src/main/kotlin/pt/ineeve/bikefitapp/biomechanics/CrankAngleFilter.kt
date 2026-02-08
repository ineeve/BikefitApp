package pt.ineeve.bikefitapp.biomechanics

import kotlin.math.abs

/**
 * Filters and smooths crank angle measurements to reduce noise and jitter from pose detection.
 * 
 * Issues addressed:
 * - Frame-to-frame angle jitter from MediaPipe pose detection
 * - Sudden angle jumps due to poor ankle landmark detection
 * - 359°→0° wrapping artifacts
 * 
 * Techniques employed:
 * 1. **Outlier Rejection**: Skip frames with unrealistic angle changes (> threshold)
 * 2. **Moving Average Smoothing**: Exponential moving average for temporal stability
 * 3. **Angle Wrapping**: Proper handling of 0°/360° discontinuity
 */
object CrankAngleFilter {
    
    /**
     * Configuration for crank angle filtering.
     * 
     * @param maxAngleChangePerFrame Maximum allowed angle change between consecutive frames (degrees)
     *                                Default 30° = reasonable pedaling cadence (~120 RPM at 30fps)
     *                                Only used as initial fallback; adaptive velocity used when available
     * @param smoothingFactor Exponential moving average factor (0-1)
     *                        0.3 = more weight on historical average, smoother
     *                        0.7 = more weight on current measurement, more responsive
     * @param velocityHistorySize Number of recent frames to use for angular velocity estimation
     * @param velocityThresholdMultiplier Outlier threshold as multiple of estimated velocity
     *                                     e.g., 2.5x means reject if delta > 2.5 × recent_velocity
     */
    data class FilterConfig(
        val maxAngleChangePerFrame: Float = 30f,
        val smoothingFactor: Float = 0.3f,
        val velocityHistorySize: Int = 5,
        val velocityThresholdMultiplier: Float = 2.5f
    )
    
    /**
     * Holds filtering state across multiple frames.
     */
    class FilterState(
        var lastValidAngle: Float? = null,
        var smoothedAngle: Float? = null,
        var framesSinceLastValid: Int = 0,
        val recentDeltas: MutableList<Float> = mutableListOf()  // Track recent angle changes for velocity estimation
    )
    
    /**
     * Result of filtering a crank angle measurement.
     * 
     * @param angle Filtered crank angle (0-360°), or null if rejected
     * @param isValid True if angle passed filters
     * @param isOutlier True if angle was rejected as outlier
     * @param angleDelta Change from previous valid angle (for diagnostics)
     */
    data class FilterResult(
        val angle: Float?,
        val isValid: Boolean,
        val isOutlier: Boolean,
        val angleDelta: Float? = null
    )
    
    /**
     * Applies crank angle filtering and smoothing to a measurement.
     * 
     * Process:
     * 1. Estimate angular velocity from recent frames
     * 2. Calculate dynamic outlier threshold based on velocity
     * 3. Check if angle change from last valid frame exceeds threshold
     * 4. If valid, apply exponential moving average smoothing
     * 5. Return smoothed angle or null if rejected
     * 
     * @param rawAngle Raw crank angle from pose detection (0-360°)
     * @param state Mutable filter state (updated in-place)
     * @param config Filter configuration
     * @return FilterResult with filtered angle and diagnostic info
     */
    fun filterAngle(
        rawAngle: Float,
        state: FilterState,
        config: FilterConfig = FilterConfig()
    ): FilterResult {
        // Validate input
        if (rawAngle !in 0f..360f) {
            return FilterResult(null, false, false, null)
        }
        
        // Check for outliers: angle change too large
        if (state.lastValidAngle != null) {
            val angleDelta = calculateAngleDelta(state.lastValidAngle!!, rawAngle)
            
            // Estimate angular velocity and calculate adaptive threshold
            val estimatedVelocity = estimateAngularVelocity(state, config)
            val adaptiveThreshold = maxOf(
                estimatedVelocity * config.velocityThresholdMultiplier,
                config.maxAngleChangePerFrame  // Never go below static threshold
            )
            
            if (abs(angleDelta) > adaptiveThreshold) {
                // Outlier rejected
                state.framesSinceLastValid++
                android.util.Log.d(
                    "CrankAngleFilter", 
                    "filterAngle OUTLIER: rejecting angle with excessive delta. raw=$rawAngle°, delta=$angleDelta°, " +
                    "velocity=${String.format("%.2f", estimatedVelocity)}°/frame, threshold=${String.format("%.2f", adaptiveThreshold)}°, " +
                    "retaining smoothed=${state.smoothedAngle}°"
                )
                return FilterResult(
                    angle = state.smoothedAngle,  // Return last smoothed value
                    isValid = false,
                    isOutlier = true,
                    angleDelta = angleDelta
                )
            }
            
            // Valid measurement - apply smoothing
            state.lastValidAngle = rawAngle
            state.framesSinceLastValid = 0
            state.recentDeltas.add(abs(angleDelta))
            if (state.recentDeltas.size > config.velocityHistorySize) {
                state.recentDeltas.removeAt(0)
            }
            
            // Exponential moving average
            val previousSmoothed = state.smoothedAngle ?: rawAngle
            state.smoothedAngle = if (state.smoothedAngle == null) {
                rawAngle
            } else {
                // New = smoothingFactor × Current + (1 - smoothingFactor) × Historical
                config.smoothingFactor * rawAngle + (1f - config.smoothingFactor) * previousSmoothed
            }
            
            android.util.Log.d(
                "CrankAngleFilter", 
                "filterAngle VALID: frame has valid angle. raw=$rawAngle°, delta=$angleDelta°, " +
                "smoothed=${state.smoothedAngle!!}°, velocity=${String.format("%.2f", estimatedVelocity)}°/frame"
            )
            
            return FilterResult(
                angle = state.smoothedAngle!!,
                isValid = true,
                isOutlier = false,
                angleDelta = angleDelta
            )
        } else {
            // First measurement
            state.lastValidAngle = rawAngle
            state.smoothedAngle = rawAngle
            state.framesSinceLastValid = 0
            
            android.util.Log.d(
                "CrankAngleFilter", 
                "filterAngle FIRST: initializing filter state. raw=$rawAngle°, smoothed=$rawAngle°"
            )
            
            return FilterResult(
                angle = rawAngle,
                isValid = true,
                isOutlier = false,
                angleDelta = null
            )
        }
    }
    
    /**
     * Estimates angular velocity (degrees per frame) from recent frame history.
     * Uses average of recent frame-to-frame angle changes.
     * 
     * If insufficient history, falls back to static threshold.
     * 
     * @param state Current filter state with recent deltas
     * @param config Filter configuration with velocity history size
     * @return Estimated angular velocity in degrees/frame
     */
    fun estimateAngularVelocity(state: FilterState, config: FilterConfig): Float {
        if (state.recentDeltas.isEmpty()) {
            return config.maxAngleChangePerFrame / config.velocityThresholdMultiplier
        }
        
        // Average of recent angle changes
        val avgDelta = state.recentDeltas.average().toFloat()
        
        // Require at least 3 recent samples for confidence
        return if (state.recentDeltas.size >= 3) {
            avgDelta
        } else {
            // Early on, blend estimated with fallback threshold
            val weight = state.recentDeltas.size / 3f
            weight * avgDelta + (1f - weight) * (config.maxAngleChangePerFrame / config.velocityThresholdMultiplier)
        }
    }
    
    /**
     * Calculates the signed angle difference between two angles, handling wrapping.
     * 
     * Examples:
     * - 10° to 20° = 10°
     * - 350° to 10° = 20° (not -340°)
     * - 180° to 179° = -1°
     * - 5° to 355° = -10° (not 350°)
     * 
     * @param fromAngle Starting angle (0-360°)
     * @param toAngle Ending angle (0-360°)
     * @return Signed angle delta (-180° to +180°)
     */
    fun calculateAngleDelta(fromAngle: Float, toAngle: Float): Float {
        var delta = toAngle - fromAngle
        
        // Normalize to [-180, 180] range
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        
        return delta
    }
    
    /**
     * Resets filter state (useful when detection is lost or restarting).
     */
    fun reset(state: FilterState) {
        state.lastValidAngle = null
        state.smoothedAngle = null
        state.framesSinceLastValid = 0
        state.recentDeltas.clear()
    }
}
