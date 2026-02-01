package pt.ineeve.bikefitapp.biomechanics

import pt.ineeve.bikefitapp.pose.Landmark
import pt.ineeve.bikefitapp.pose.PoseFrame
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import kotlin.math.abs

/**
 * Represents the direction of knee position relative to pedal.
 */
enum class KneeAlignment {
    /** Knee is positioned forward of the pedal */
    FORWARD,
    /** Knee is positioned rearward of the pedal */
    REARWARD,
    /** Knee is directly aligned with the pedal */
    NEUTRAL
}

/**
 * Result of knee-over-pedal offset calculation.
 * 
 * @param normalizedOffset Horizontal offset normalized by femur length (dimensionless)
 * @param alignment Directional bias of knee position
 * @param rawOffset Raw horizontal distance from knee to ankle/pedal in normalized coordinates
 * @param femurLength Length of femur (hip to knee) used for normalization
 * @param side Which leg was analyzed
 * @param frameNumber Frame number where measurement was taken
 * @param timestampMs Timestamp at measurement
 * @param confidence Measurement confidence based on landmark visibility
 * @param isValid Whether the result is valid
 */
data class KneeOverPedalOffsetResult(
    val normalizedOffset: Float,
    val alignment: KneeAlignment,
    val rawOffset: Float,
    val femurLength: Float,
    val side: BodySide,
    val frameNumber: Long,
    val timestampMs: Long,
    val confidence: Float,
    val isValid: Boolean
) {
    companion object {
        /**
         * Creates an invalid result when computation fails.
         */
        fun invalid(side: BodySide): KneeOverPedalOffsetResult {
            return KneeOverPedalOffsetResult(
                normalizedOffset = 0f,
                alignment = KneeAlignment.NEUTRAL,
                rawOffset = 0f,
                femurLength = 0f,
                side = side,
                frameNumber = 0L,
                timestampMs = 0L,
                confidence = 0f,
                isValid = false
            )
        }
    }
}

/**
 * Summary of knee-over-pedal offset measurements across multiple cycles.
 * 
 * @param measurementCount Number of measurements included
 * @param averageNormalizedOffset Average normalized offset
 * @param minNormalizedOffset Minimum normalized offset observed
 * @param maxNormalizedOffset Maximum normalized offset observed
 * @param standardDeviation Standard deviation of normalized offsets
 * @param averageAlignment Most common alignment direction
 * @param side Which leg was analyzed
 * @param isValid Whether the summary contains valid data
 */
data class KneeOverPedalOffsetSummary(
    val measurementCount: Int,
    val averageNormalizedOffset: Float,
    val minNormalizedOffset: Float,
    val maxNormalizedOffset: Float,
    val standardDeviation: Float,
    val averageAlignment: KneeAlignment,
    val side: BodySide,
    val isValid: Boolean
) {
    companion object {
        /**
         * Creates an invalid summary when no valid data is available.
         */
        fun invalid(side: BodySide): KneeOverPedalOffsetSummary {
            return KneeOverPedalOffsetSummary(
                measurementCount = 0,
                averageNormalizedOffset = 0f,
                minNormalizedOffset = 0f,
                maxNormalizedOffset = 0f,
                standardDeviation = 0f,
                averageAlignment = KneeAlignment.NEUTRAL,
                side = side,
                isValid = false
            )
        }
    }
}

/**
 * Configuration for knee-over-pedal offset computation.
 * 
 * @param visibilityThreshold Minimum visibility for landmarks
 * @param neutralThreshold Threshold for considering alignment neutral (as fraction of femur)
 */
data class KneeOverPedalOffsetConfig(
    val visibilityThreshold: Float = 0.5f,
    val neutralThreshold: Float = 0.05f
)

/**
 * Computes normalized knee-over-pedal offset at 3 o'clock crank position.
 * 
 * This metric estimates knee position relative to the pedal using a normalized,
 * scale-free distance. The measurement is taken when the crank is at 3 o'clock
 * (horizontal forward position, where the knee is typically most forward).
 * 
 * The offset is normalized by femur length (hip to knee distance) to provide
 * a scale-free metric that can be compared across different body sizes.
 * 
 * Directional labeling:
 * - FORWARD: Knee is ahead of the pedal (positive offset)
 * - REARWARD: Knee is behind the pedal (negative offset)
 * - NEUTRAL: Knee is aligned with pedal (within threshold)
 * 
 * Usage:
 * ```
 * // At 3 o'clock position frame
 * val result = KneeOverPedalOffset.computeAtFrame(
 *     frame = poseFrame,
 *     side = BodySide.LEFT
 * )
 * 
 * if (result.isValid) {
 *     println("Normalized offset: ${result.normalizedOffset}")
 *     println("Alignment: ${result.alignment}")
 * }
 * ```
 * 
 * All functions are pure and stateless.
 */
object KneeOverPedalOffset {

    /**
     * Computes knee-over-pedal offset at a single frame (assumed to be at 3 o'clock).
     * 
     * The 3 o'clock position is when the crank is horizontal forward.
     * At this position, we measure:
     * 1. Horizontal distance from knee to ankle (pedal proxy)
     * 2. Femur length (hip to knee)
     * 3. Normalized offset = horizontal distance / femur length
     * 
     * @param frame The pose frame at 3 o'clock position
     * @param side Which leg to analyze
     * @param config Configuration options
     * @return Result containing normalized offset and alignment
     */
    fun computeAtFrame(
        frame: PoseFrame,
        side: BodySide,
        config: KneeOverPedalOffsetConfig = KneeOverPedalOffsetConfig()
    ): KneeOverPedalOffsetResult {
        if (frame.landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return KneeOverPedalOffsetResult.invalid(side)
        }

        // Get landmark indices based on side
        val (hipIndex, kneeIndex, ankleIndex) = getLandmarkIndices(side)

        // Get the landmarks
        val hip = frame.landmarks[hipIndex]
        val knee = frame.landmarks[kneeIndex]
        val ankle = frame.landmarks[ankleIndex]

        // Check visibility
        if (!hip.isVisible(config.visibilityThreshold) ||
            !knee.isVisible(config.visibilityThreshold) ||
            !ankle.isVisible(config.visibilityThreshold)) {
            return KneeOverPedalOffsetResult.invalid(side)
        }

        // Calculate average confidence
        val confidence = (hip.visibility + knee.visibility + ankle.visibility) / 3f

        // Compute the offset
        val components = computeOffset(hip, knee, ankle, config)

        return KneeOverPedalOffsetResult(
            normalizedOffset = components.normalizedOffset,
            alignment = components.alignment,
            rawOffset = components.rawOffset,
            femurLength = components.femurLength,
            side = side,
            frameNumber = frame.frameNumber,
            timestampMs = frame.timestampMs,
            confidence = confidence,
            isValid = true
        )
    }

    /**
     * Computes knee-over-pedal offset from raw landmarks.
     * 
     * @param landmarks List of 33 pose landmarks
     * @param side Which leg to analyze
     * @param config Configuration options
     * @return Result containing normalized offset and alignment
     */
    fun computeFromLandmarks(
        landmarks: List<Landmark>,
        side: BodySide,
        config: KneeOverPedalOffsetConfig = KneeOverPedalOffsetConfig()
    ): KneeOverPedalOffsetResult {
        if (landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return KneeOverPedalOffsetResult.invalid(side)
        }

        // Get landmark indices based on side
        val (hipIndex, kneeIndex, ankleIndex) = getLandmarkIndices(side)

        // Get the landmarks
        val hip = landmarks[hipIndex]
        val knee = landmarks[kneeIndex]
        val ankle = landmarks[ankleIndex]

        // Check visibility
        if (!hip.isVisible(config.visibilityThreshold) ||
            !knee.isVisible(config.visibilityThreshold) ||
            !ankle.isVisible(config.visibilityThreshold)) {
            return KneeOverPedalOffsetResult.invalid(side)
        }

        // Calculate average confidence
        val confidence = (hip.visibility + knee.visibility + ankle.visibility) / 3f

        // Compute the offset
        val components = computeOffset(hip, knee, ankle, config)

        return KneeOverPedalOffsetResult(
            normalizedOffset = components.normalizedOffset,
            alignment = components.alignment,
            rawOffset = components.rawOffset,
            femurLength = components.femurLength,
            side = side,
            frameNumber = 0L,
            timestampMs = 0L,
            confidence = confidence,
            isValid = true
        )
    }

    /**
     * Gets the landmark indices for the specified body side.
     * 
     * @param side Which leg to analyze
     * @return Triple of (hip index, knee index, ankle index)
     */
    private fun getLandmarkIndices(side: BodySide): Triple<Int, Int, Int> {
        return if (side == BodySide.LEFT) {
            Triple(
                PoseLandmarkIndex.LEFT_HIP,
                PoseLandmarkIndex.LEFT_KNEE,
                PoseLandmarkIndex.LEFT_ANKLE
            )
        } else {
            Triple(
                PoseLandmarkIndex.RIGHT_HIP,
                PoseLandmarkIndex.RIGHT_KNEE,
                PoseLandmarkIndex.RIGHT_ANKLE
            )
        }
    }

    /**
     * Internal function to compute the normalized offset.
     * 
     * @param hip Hip landmark
     * @param knee Knee landmark
     * @param ankle Ankle landmark (proxy for pedal position)
     * @param config Configuration options
     * @return Offset components including normalized and raw values
     */
    private fun computeOffset(
        hip: Landmark,
        knee: Landmark,
        ankle: Landmark,
        config: KneeOverPedalOffsetConfig
    ): OffsetComponents {
        // Calculate horizontal offset (X-axis difference)
        // Positive = knee forward of ankle, Negative = knee behind ankle
        val horizontalOffset = knee.x - ankle.x

        // Calculate femur length (hip to knee distance)
        val hipPoint = Vector2D(hip.x, hip.y)
        val kneePoint = Vector2D(knee.x, knee.y)
        val femurLength = hipPoint.distanceTo(kneePoint)

        // Guard against zero femur length
        if (femurLength < Vector2D.EPSILON) {
            return OffsetComponents(0f, KneeAlignment.NEUTRAL, horizontalOffset, femurLength)
        }

        // Normalize offset by femur length
        val normalizedOffset = horizontalOffset / femurLength

        // Determine alignment based on normalized offset
        val alignment = when {
            abs(normalizedOffset) < config.neutralThreshold -> KneeAlignment.NEUTRAL
            normalizedOffset > 0f -> KneeAlignment.FORWARD
            else -> KneeAlignment.REARWARD
        }

        return OffsetComponents(normalizedOffset, alignment, horizontalOffset, femurLength)
    }

    /**
     * Computes knee-over-pedal offset from multiple frames and returns a summary.
     * 
     * This is useful for analyzing offset across multiple 3 o'clock positions
     * in a recording to get stable average measurements.
     * 
     * @param frames List of pose frames (should all be at or near 3 o'clock position)
     * @param side Which leg to analyze
     * @param config Configuration options
     * @return Summary of offset measurements across all frames
     */
    fun computeFromFrames(
        frames: List<PoseFrame>,
        side: BodySide,
        config: KneeOverPedalOffsetConfig = KneeOverPedalOffsetConfig()
    ): KneeOverPedalOffsetSummary {
        if (frames.isEmpty()) {
            return KneeOverPedalOffsetSummary.invalid(side)
        }

        // Compute offset for each frame
        val results = frames.mapNotNull { frame ->
            val result = computeAtFrame(frame, side, config)
            if (result.isValid) result else null
        }

        if (results.isEmpty()) {
            return KneeOverPedalOffsetSummary.invalid(side)
        }

        // Extract normalized offsets
        val normalizedOffsets = results.map { it.normalizedOffset }

        // Calculate statistics
        val average = normalizedOffsets.average().toFloat()
        val min = normalizedOffsets.minOrNull() ?: 0f
        val max = normalizedOffsets.maxOrNull() ?: 0f
        val stdDev = calculateStandardDeviation(normalizedOffsets, average)

        // Determine most common alignment
        val alignmentCounts = results.groupingBy { it.alignment }.eachCount()
        val averageAlignment = alignmentCounts.maxByOrNull { it.value }?.key ?: KneeAlignment.NEUTRAL

        return KneeOverPedalOffsetSummary(
            measurementCount = results.size,
            averageNormalizedOffset = average,
            minNormalizedOffset = min,
            maxNormalizedOffset = max,
            standardDeviation = stdDev,
            averageAlignment = averageAlignment,
            side = side,
            isValid = true
        )
    }

    /**
     * Calculates standard deviation of a list of values.
     * 
     * @param values List of values
     * @param mean Mean of the values
     * @return Standard deviation
     */
    private fun calculateStandardDeviation(values: List<Float>, mean: Float): Float {
        if (values.size < 2) {
            return 0f
        }

        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }
}

/**
 * Internal data class for offset computation components.
 * 
 * @param normalizedOffset Offset normalized by femur length
 * @param alignment Direction of knee position
 * @param rawOffset Raw horizontal distance
 * @param femurLength Length of femur used for normalization
 */
private data class OffsetComponents(
    val normalizedOffset: Float,
    val alignment: KneeAlignment,
    val rawOffset: Float,
    val femurLength: Float
)
