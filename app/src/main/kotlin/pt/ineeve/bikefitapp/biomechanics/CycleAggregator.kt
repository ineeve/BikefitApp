package pt.ineeve.bikefitapp.biomechanics

/**
 * Internal data structure for tracking measurements during a cycle.
 */
internal data class CycleMeasurements(
    val kneeAngles: MutableList<Float> = mutableListOf(),
    val hipAngles: MutableList<Float> = mutableListOf(),
    val torsoAngles: MutableList<Float> = mutableListOf(),
    var kneeAngleAtBdc: Float? = null,
    var kneeAngleAtTdc: Float? = null,
    var startFrameNumber: Long = 0,
    var endFrameNumber: Long = 0,
    var startTimestampMs: Long = 0,
    var endTimestampMs: Long = 0
)

/**
 * Aggregates biomechanical measurements per pedal cycle.
 * 
 * This class collects angle measurements during pedaling and groups them
 * into complete cycles based on pedal extrema (BDC/TDC). It calculates
 * average, min, max statistics for each cycle to produce stable metrics
 * for the fit engine.
 * 
 * Usage:
 * ```
 * val aggregator = CycleAggregator(BodySide.LEFT)
 * 
 * // As each frame is processed:
 * aggregator.addMeasurement(
 *     frameNumber = frame.frameNumber,
 *     timestampMs = frame.timestampMs,
 *     kneeAngle = kneeResult.angle,
 *     hipAngle = hipResult.angle,
 *     torsoAngle = torsoResult.angle
 * )
 * 
 * // When a pedal extremum is detected:
 * if (extremum.type == PedalExtremum.BDC) {
 *     val completedCycle = aggregator.endCycleAtBdc(
 *         frameNumber = extremum.frameNumber,
 *         timestampMs = extremum.timestampMs,
 *         kneeAngleAtBdc = currentKneeAngle
 *     )
 * }
 * 
 * // Get final summary:
 * val summary = aggregator.getSummary()
 * ```
 * 
 * @param side Which body side to track
 */
class CycleAggregator(
    private val side: BodySide
) {
    private val completedCycles = mutableListOf<CycleMetrics>()
    private var currentMeasurements = CycleMeasurements()
    private var cycleNumber = 0
    private var cycleStarted = false

    /**
     * Adds a measurement to the current cycle.
     * 
     * @param frameNumber Frame number for this measurement
     * @param timestampMs Timestamp in milliseconds
     * @param kneeAngle Current knee angle (may be null if not visible)
     * @param hipAngle Current hip angle (may be null if not visible)
     * @param torsoAngle Current torso angle (may be null if not visible)
     */
    fun addMeasurement(
        frameNumber: Long,
        timestampMs: Long,
        kneeAngle: Float? = null,
        hipAngle: Float? = null,
        torsoAngle: Float? = null
    ) {
        // Initialize cycle start if this is the first measurement
        if (!cycleStarted) {
            currentMeasurements.startFrameNumber = frameNumber
            currentMeasurements.startTimestampMs = timestampMs
            cycleStarted = true
        }

        // Add valid measurements
        kneeAngle?.let { currentMeasurements.kneeAngles.add(it) }
        hipAngle?.let { currentMeasurements.hipAngles.add(it) }
        torsoAngle?.let { currentMeasurements.torsoAngles.add(it) }

        // Update end frame/timestamp
        currentMeasurements.endFrameNumber = frameNumber
        currentMeasurements.endTimestampMs = timestampMs
    }

    /**
     * Marks bottom dead center and optionally ends the current cycle.
     * 
     * If this is not the first BDC, it completes the current cycle and
     * starts a new one.
     * 
     * @param frameNumber Frame number at BDC
     * @param timestampMs Timestamp at BDC
     * @param kneeAngle Knee angle at BDC
     * @return Completed CycleMetrics, or null if this is the first BDC
     */
    fun endCycleAtBdc(
        frameNumber: Long,
        timestampMs: Long,
        kneeAngle: Float?
    ): CycleMetrics? {
        // Record knee angle at BDC for current cycle
        currentMeasurements.kneeAngleAtBdc = kneeAngle

        // If we have accumulated measurements, complete the cycle
        val completed = if (cycleStarted && hasMeasurements()) {
            finalizeCycle(frameNumber, timestampMs)
        } else {
            null
        }

        // Start new cycle
        startNewCycle(frameNumber, timestampMs)

        return completed
    }

    /**
     * Records top dead center in the current cycle.
     * 
     * @param kneeAngle Knee angle at TDC
     */
    fun recordTdc(kneeAngle: Float?) {
        currentMeasurements.kneeAngleAtTdc = kneeAngle
    }

    /**
     * Ends the current cycle at TDC.
     * 
     * @param frameNumber Frame number at TDC
     * @param timestampMs Timestamp at TDC
     * @param kneeAngle Knee angle at TDC
     * @return Completed CycleMetrics, or null if no valid data
     */
    fun endCycleAtTdc(
        frameNumber: Long,
        timestampMs: Long,
        kneeAngle: Float?
    ): CycleMetrics? {
        currentMeasurements.kneeAngleAtTdc = kneeAngle

        val completed = if (cycleStarted && hasMeasurements()) {
            finalizeCycle(frameNumber, timestampMs)
        } else {
            null
        }

        startNewCycle(frameNumber, timestampMs)

        return completed
    }

    /**
     * Gets all completed cycles.
     */
    fun getCompletedCycles(): List<CycleMetrics> = completedCycles.toList()

    /**
     * Gets the number of completed cycles.
     */
    fun getCycleCount(): Int = completedCycles.size

    /**
     * Gets a summary of all completed cycles.
     */
    fun getSummary(): CycleSummary {
        if (completedCycles.isEmpty()) {
            return CycleSummary.invalid(side)
        }

        // Collect BDC/TDC angles from all cycles
        val bdcAngles = completedCycles.mapNotNull { it.kneeAngleAtBdc }
        val tdcAngles = completedCycles.mapNotNull { it.kneeAngleAtTdc }

        // Calculate average ranges
        val kneeRanges = completedCycles.map { it.kneeAngle.range }
        val hipAverages = completedCycles.map { it.hipAngle.average }
        val torsoAverages = completedCycles.map { it.torsoAngle.average }
        val cadences = completedCycles.mapNotNull { it.cadenceRpm }

        return CycleSummary(
            cycleCount = completedCycles.size,
            averageKneeAngleAtBdc = if (bdcAngles.isNotEmpty()) bdcAngles.average().toFloat() else null,
            averageKneeAngleAtTdc = if (tdcAngles.isNotEmpty()) tdcAngles.average().toFloat() else null,
            averageKneeAngleRange = if (kneeRanges.isNotEmpty()) kneeRanges.average().toFloat() else 0f,
            averageHipAngle = if (hipAverages.isNotEmpty()) hipAverages.average().toFloat() else 0f,
            averageTorsoAngle = if (torsoAverages.isNotEmpty()) torsoAverages.average().toFloat() else 0f,
            averageCadenceRpm = if (cadences.isNotEmpty()) cadences.average().toFloat() else null,
            kneeAngleAtBdcStats = AngleStats.fromValues(bdcAngles),
            kneeAngleAtTdcStats = AngleStats.fromValues(tdcAngles),
            hipAngleStats = AngleStats.fromValues(
                completedCycles.flatMap { 
                    listOf(it.hipAngle.min, it.hipAngle.average, it.hipAngle.max)
                }
            ),
            torsoAngleStats = AngleStats.fromValues(
                completedCycles.flatMap {
                    listOf(it.torsoAngle.min, it.torsoAngle.average, it.torsoAngle.max)
                }
            ),
            side = side
        )
    }

    /**
     * Gets the most recent completed cycle.
     */
    fun getLastCycle(): CycleMetrics? = completedCycles.lastOrNull()

    /**
     * Clears all data and resets the aggregator.
     */
    fun reset() {
        completedCycles.clear()
        currentMeasurements = CycleMeasurements()
        cycleNumber = 0
        cycleStarted = false
    }

    /**
     * Returns the side being tracked.
     */
    fun getSide(): BodySide = side

    /**
     * Returns true if there are measurements in the current cycle.
     */
    private fun hasMeasurements(): Boolean {
        return currentMeasurements.kneeAngles.isNotEmpty() ||
               currentMeasurements.hipAngles.isNotEmpty() ||
               currentMeasurements.torsoAngles.isNotEmpty()
    }

    /**
     * Finalizes the current cycle and adds it to completed cycles.
     */
    private fun finalizeCycle(endFrame: Long, endTimestamp: Long): CycleMetrics {
        currentMeasurements.endFrameNumber = endFrame
        currentMeasurements.endTimestampMs = endTimestamp

        val metrics = CycleMetrics(
            cycleNumber = cycleNumber,
            startFrameNumber = currentMeasurements.startFrameNumber,
            endFrameNumber = currentMeasurements.endFrameNumber,
            startTimestampMs = currentMeasurements.startTimestampMs,
            endTimestampMs = currentMeasurements.endTimestampMs,
            kneeAngle = AngleStats.fromValues(currentMeasurements.kneeAngles),
            hipAngle = AngleStats.fromValues(currentMeasurements.hipAngles),
            torsoAngle = AngleStats.fromValues(currentMeasurements.torsoAngles),
            kneeAngleAtBdc = currentMeasurements.kneeAngleAtBdc,
            kneeAngleAtTdc = currentMeasurements.kneeAngleAtTdc,
            side = side
        )

        completedCycles.add(metrics)
        cycleNumber++

        return metrics
    }

    /**
     * Starts a new cycle.
     */
    private fun startNewCycle(frameNumber: Long, timestampMs: Long) {
        currentMeasurements = CycleMeasurements(
            startFrameNumber = frameNumber,
            startTimestampMs = timestampMs
        )
        cycleStarted = true
    }

    companion object {
        /**
         * Aggregates a list of CycleMetrics into a CycleSummary.
         * 
         * @param cycles List of cycle metrics to aggregate
         * @param side Body side for the summary
         * @return CycleSummary with aggregated statistics
         */
        fun aggregateCycles(cycles: List<CycleMetrics>, side: BodySide): CycleSummary {
            if (cycles.isEmpty()) {
                return CycleSummary.invalid(side)
            }

            val bdcAngles = cycles.mapNotNull { it.kneeAngleAtBdc }
            val tdcAngles = cycles.mapNotNull { it.kneeAngleAtTdc }
            val kneeRanges = cycles.map { it.kneeAngle.range }
            val hipAverages = cycles.filter { it.hipAngle.isValid }.map { it.hipAngle.average }
            val torsoAverages = cycles.filter { it.torsoAngle.isValid }.map { it.torsoAngle.average }
            val cadences = cycles.mapNotNull { it.cadenceRpm }

            return CycleSummary(
                cycleCount = cycles.size,
                averageKneeAngleAtBdc = if (bdcAngles.isNotEmpty()) bdcAngles.average().toFloat() else null,
                averageKneeAngleAtTdc = if (tdcAngles.isNotEmpty()) tdcAngles.average().toFloat() else null,
                averageKneeAngleRange = if (kneeRanges.isNotEmpty()) kneeRanges.average().toFloat() else 0f,
                averageHipAngle = if (hipAverages.isNotEmpty()) hipAverages.average().toFloat() else 0f,
                averageTorsoAngle = if (torsoAverages.isNotEmpty()) torsoAverages.average().toFloat() else 0f,
                averageCadenceRpm = if (cadences.isNotEmpty()) cadences.average().toFloat() else null,
                kneeAngleAtBdcStats = AngleStats.fromValues(bdcAngles),
                kneeAngleAtTdcStats = AngleStats.fromValues(tdcAngles),
                hipAngleStats = AngleStats.fromValues(
                    cycles.filter { it.hipAngle.isValid }.flatMap {
                        listOf(it.hipAngle.min, it.hipAngle.average, it.hipAngle.max)
                    }
                ),
                torsoAngleStats = AngleStats.fromValues(
                    cycles.filter { it.torsoAngle.isValid }.flatMap {
                        listOf(it.torsoAngle.min, it.torsoAngle.average, it.torsoAngle.max)
                    }
                ),
                side = side
            )
        }
    }
}
