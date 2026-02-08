package pt.ineeve.bikefitapp.biomechanics

/**
 * Wrapper for applying Savitzky-Golay filtering to joint angle time series.
 * 
 * This class maintains separate filters for different angle types (knee, hip, ankle, torso)
 * and applies smoothing to reduce noise while preserving extrema shape and ensuring
 * zero phase shift.
 * 
 * Configuration:
 * - Default for 60 FPS: windowSize=11, polynomialOrder=3
 * - Customizable per use case
 * 
 * Usage:
 * ```
 * val angleFilter = JointAngleFilter()
 * 
 * // In frame processing loop:
 * val kneeAngle = calculateKneeAngle(...)
 * val smoothedKnee = angleFilter.filterKneeAngle(kneeAngle)
 * 
 * // Use smoothedKnee (may be null until buffer is full)
 * ```
 * 
 * @param windowSize Size of the smoothing window (must be odd, default=11 for 60 FPS)
 * @param polynomialOrder Order of polynomial fit (default=3)
 */
class JointAngleFilter(
    windowSize: Int = 11,
    polynomialOrder: Int = 3
) {
    private val kneeFilter = SavitzkyGolayFilter(windowSize, polynomialOrder)
    private val hipFilter = SavitzkyGolayFilter(windowSize, polynomialOrder)
    private val ankleFilter = SavitzkyGolayFilter(windowSize, polynomialOrder)
    private val torsoFilter = SavitzkyGolayFilter(windowSize, polynomialOrder)
    
    /**
     * Filters knee angle measurement.
     * 
     * @param angle Raw knee angle in degrees, or null if not available
     * @return Smoothed knee angle in degrees, or null if buffer not full or input is null
     */
    fun filterKneeAngle(angle: Float?): Float? {
        if (angle == null) return null
        return kneeFilter.addSample(angle.toDouble())?.toFloat()
    }
    
    /**
     * Filters hip angle measurement.
     * 
     * @param angle Raw hip angle in degrees, or null if not available
     * @return Smoothed hip angle in degrees, or null if buffer not full or input is null
     */
    fun filterHipAngle(angle: Float?): Float? {
        if (angle == null) return null
        return hipFilter.addSample(angle.toDouble())?.toFloat()
    }
    
    /**
     * Filters ankle angle measurement.
     * 
     * @param angle Raw ankle angle in degrees, or null if not available
     * @return Smoothed ankle angle in degrees, or null if buffer not full or input is null
     */
    fun filterAnkleAngle(angle: Float?): Float? {
        if (angle == null) return null
        return ankleFilter.addSample(angle.toDouble())?.toFloat()
    }
    
    /**
     * Filters torso angle measurement.
     * 
     * @param angle Raw torso angle in degrees, or null if not available
     * @return Smoothed torso angle in degrees, or null if buffer not full or input is null
     */
    fun filterTorsoAngle(angle: Float?): Float? {
        if (angle == null) return null
        return torsoFilter.addSample(angle.toDouble())?.toFloat()
    }
    
    /**
     * Resets all filters, clearing their internal state.
     * Call this when starting a new analysis session.
     */
    fun reset() {
        kneeFilter.reset()
        hipFilter.reset()
        ankleFilter.reset()
        torsoFilter.reset()
    }
}
