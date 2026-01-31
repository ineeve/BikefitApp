package pt.ineeve.bikefitapp.biomechanics

/**
 * Aggregated statistics for a single measurement type.
 * 
 * @param min Minimum value observed during the cycle
 * @param max Maximum value observed during the cycle
 * @param average Mean value during the cycle
 * @param sampleCount Number of samples used in the calculation
 */
data class AngleStats(
    val min: Float,
    val max: Float,
    val average: Float,
    val sampleCount: Int
) {
    /**
     * Range of the angle (max - min).
     */
    val range: Float
        get() = max - min

    /**
     * True if this represents valid data.
     */
    val isValid: Boolean
        get() = sampleCount > 0

    companion object {
        /**
         * Invalid/empty stats when no data is available.
         */
        val INVALID = AngleStats(
            min = 0f,
            max = 0f,
            average = 0f,
            sampleCount = 0
        )

        /**
         * Creates AngleStats from a list of values.
         */
        fun fromValues(values: List<Float>): AngleStats {
            if (values.isEmpty()) return INVALID

            return AngleStats(
                min = values.minOrNull() ?: 0f,
                max = values.maxOrNull() ?: 0f,
                average = values.average().toFloat(),
                sampleCount = values.size
            )
        }
    }
}

/**
 * Aggregated biomechanical metrics for a single pedal cycle.
 * 
 * This represents all the key measurements from one complete pedal revolution
 * (from BDC to BDC or TDC to TDC). These stable per-cycle metrics are used
 * by the fit engine to generate recommendations.
 * 
 * For bike fit analysis, key metrics include:
 * - Knee angle at BDC: Should be ~25-35° (near full extension)
 * - Knee angle at TDC: Should be ~70-110° depending on fit style
 * - Hip angle: Affects comfort and power transfer
 * - Torso angle: Affects aerodynamics and comfort
 * 
 * @param cycleNumber Sequential cycle number in the recording
 * @param startFrameNumber Frame number at cycle start
 * @param endFrameNumber Frame number at cycle end
 * @param startTimestampMs Timestamp at cycle start
 * @param endTimestampMs Timestamp at cycle end
 * @param kneeAngle Knee angle statistics during the cycle
 * @param hipAngle Hip angle statistics during the cycle
 * @param torsoAngle Torso angle statistics during the cycle
 * @param kneeAngleAtBdc Knee angle specifically at bottom dead center
 * @param kneeAngleAtTdc Knee angle specifically at top dead center
 * @param side Which side this cycle represents
 */
data class CycleMetrics(
    val cycleNumber: Int,
    val startFrameNumber: Long,
    val endFrameNumber: Long,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val kneeAngle: AngleStats,
    val hipAngle: AngleStats,
    val torsoAngle: AngleStats,
    val kneeAngleAtBdc: Float?,
    val kneeAngleAtTdc: Float?,
    val side: BodySide
) {
    /**
     * Duration of this cycle in milliseconds.
     */
    val durationMs: Long
        get() = endTimestampMs - startTimestampMs

    /**
     * Number of frames in this cycle.
     */
    val frameCount: Long
        get() = endFrameNumber - startFrameNumber

    /**
     * Cadence for this cycle in RPM (revolutions per minute).
     * Returns null if duration is zero.
     */
    val cadenceRpm: Float?
        get() {
            val durationMinutes = durationMs / 60000f
            return if (durationMinutes > 0) 1f / durationMinutes else null
        }

    /**
     * True if this cycle has valid measurements.
     */
    val isValid: Boolean
        get() = kneeAngle.isValid && frameCount > 0

    companion object {
        /**
         * Creates an invalid/empty CycleMetrics.
         */
        fun invalid(side: BodySide = BodySide.LEFT): CycleMetrics {
            return CycleMetrics(
                cycleNumber = -1,
                startFrameNumber = 0,
                endFrameNumber = 0,
                startTimestampMs = 0,
                endTimestampMs = 0,
                kneeAngle = AngleStats.INVALID,
                hipAngle = AngleStats.INVALID,
                torsoAngle = AngleStats.INVALID,
                kneeAngleAtBdc = null,
                kneeAngleAtTdc = null,
                side = side
            )
        }
    }
}

/**
 * Summary statistics across multiple pedal cycles.
 * 
 * This aggregates CycleMetrics across many cycles to provide
 * stable overall measurements for the entire recording session.
 * 
 * @param cycleCount Number of cycles included
 * @param averageKneeAngleAtBdc Average knee angle at bottom dead center
 * @param averageKneeAngleAtTdc Average knee angle at top dead center  
 * @param averageKneeAngleRange Average range of knee motion
 * @param averageHipAngle Average hip angle across cycles
 * @param averageTorsoAngle Average torso angle across cycles
 * @param averageCadenceRpm Average cadence in RPM
 * @param kneeAngleAtBdcStats Full stats for knee angle at BDC
 * @param kneeAngleAtTdcStats Full stats for knee angle at TDC
 * @param hipAngleStats Full stats for hip angle
 * @param torsoAngleStats Full stats for torso angle
 * @param side Which side this summary represents
 */
data class CycleSummary(
    val cycleCount: Int,
    val averageKneeAngleAtBdc: Float?,
    val averageKneeAngleAtTdc: Float?,
    val averageKneeAngleRange: Float,
    val averageHipAngle: Float,
    val averageTorsoAngle: Float,
    val averageCadenceRpm: Float?,
    val kneeAngleAtBdcStats: AngleStats,
    val kneeAngleAtTdcStats: AngleStats,
    val hipAngleStats: AngleStats,
    val torsoAngleStats: AngleStats,
    val side: BodySide
) {
    /**
     * True if this summary has valid data.
     */
    val isValid: Boolean
        get() = cycleCount > 0

    companion object {
        /**
         * Creates an invalid/empty summary.
         */
        fun invalid(side: BodySide = BodySide.LEFT): CycleSummary {
            return CycleSummary(
                cycleCount = 0,
                averageKneeAngleAtBdc = null,
                averageKneeAngleAtTdc = null,
                averageKneeAngleRange = 0f,
                averageHipAngle = 0f,
                averageTorsoAngle = 0f,
                averageCadenceRpm = null,
                kneeAngleAtBdcStats = AngleStats.INVALID,
                kneeAngleAtTdcStats = AngleStats.INVALID,
                hipAngleStats = AngleStats.INVALID,
                torsoAngleStats = AngleStats.INVALID,
                side = side
            )
        }
    }
}
