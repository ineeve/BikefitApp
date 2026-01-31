package pt.ineeve.bikefitapp.biomechanics

import pt.ineeve.bikefitapp.pose.Landmark
import pt.ineeve.bikefitapp.pose.PoseFrame
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex

/**
 * Represents a detected extremum in the pedal cycle.
 */
enum class PedalExtremum {
    /** Bottom Dead Center - ankle at lowest point */
    BDC,
    /** Top Dead Center - ankle at highest point */
    TDC
}

/**
 * Represents a detected pedal cycle extremum with timing information.
 * 
 * @param type The type of extremum (BDC or TDC)
 * @param frameNumber The frame number where the extremum was detected
 * @param timestampMs The timestamp in milliseconds
 * @param ankleY The Y coordinate of the ankle at the extremum
 * @param side Which leg was analyzed
 * @param confidence Detection confidence based on the clarity of the extremum
 */
data class PedalExtremumEvent(
    val type: PedalExtremum,
    val frameNumber: Long,
    val timestampMs: Long,
    val ankleY: Float,
    val side: BodySide,
    val confidence: Float
)

/**
 * Configuration for the pedal cycle detector.
 * 
 * @param windowSize Number of frames to use for sliding window extrema detection
 * @param minCycleFrames Minimum frames between extrema of same type (prevents noise)
 * @param visibilityThreshold Minimum visibility for ankle landmark
 */
data class PedalCycleDetectorConfig(
    val windowSize: Int = 5,
    val minCycleFrames: Int = 5,
    val visibilityThreshold: Float = 0.5f
)

/**
 * Detects Bottom Dead Center (BDC) and Top Dead Center (TDC) in the pedal stroke.
 * 
 * BDC is when the ankle is at its lowest point (maximum Y in image coordinates).
 * TDC is when the ankle is at its highest point (minimum Y in image coordinates).
 * 
 * The detector uses a sliding window approach to find local extrema while
 * filtering out noise from the pose detection.
 * 
 * Usage:
 * ```
 * val detector = PedalCycleDetector()
 * 
 * // Process frames as they come in
 * for (frame in poseFrames) {
 *     val events = detector.processFrame(frame, BodySide.LEFT)
 *     for (event in events) {
 *         when (event.type) {
 *             PedalExtremum.BDC -> println("Bottom dead center at frame ${event.frameNumber}")
 *             PedalExtremum.TDC -> println("Top dead center at frame ${event.frameNumber}")
 *         }
 *     }
 * }
 * ```
 */
class PedalCycleDetector(
    private val config: PedalCycleDetectorConfig = PedalCycleDetectorConfig()
) {
    // Sliding window buffer for ankle Y positions
    private data class FrameData(
        val frameNumber: Long,
        val timestampMs: Long,
        val ankleY: Float,
        val visibility: Float
    )

    private val leftBuffer = ArrayDeque<FrameData>(config.windowSize * 2)
    private val rightBuffer = ArrayDeque<FrameData>(config.windowSize * 2)

    // Track last detected extrema to prevent duplicates
    private var lastLeftBdcFrame: Long = Long.MIN_VALUE
    private var lastLeftTdcFrame: Long = Long.MIN_VALUE
    private var lastRightBdcFrame: Long = Long.MIN_VALUE
    private var lastRightTdcFrame: Long = Long.MIN_VALUE

    /**
     * Processes a pose frame and returns any detected extrema.
     * 
     * @param poseFrame The pose frame to process
     * @param side Which leg to analyze
     * @return List of detected extrema events (may be empty)
     */
    fun processFrame(poseFrame: PoseFrame, side: BodySide): List<PedalExtremumEvent> {
        if (poseFrame.landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return emptyList()
        }

        val ankleIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_ANKLE
        } else {
            PoseLandmarkIndex.RIGHT_ANKLE
        }

        val ankle = poseFrame.landmarks[ankleIndex]

        if (!ankle.isVisible(config.visibilityThreshold)) {
            return emptyList()
        }

        val frameData = FrameData(
            frameNumber = poseFrame.frameNumber,
            timestampMs = poseFrame.timestampMs,
            ankleY = ankle.y,
            visibility = ankle.visibility
        )

        val buffer = if (side == BodySide.LEFT) leftBuffer else rightBuffer
        buffer.addLast(frameData)

        // Keep buffer size manageable
        while (buffer.size > config.windowSize * 2) {
            buffer.removeFirst()
        }

        // Need at least windowSize frames to detect extrema
        if (buffer.size < config.windowSize) {
            return emptyList()
        }

        return detectExtrema(buffer, side)
    }

    /**
     * Processes a single ankle position for extrema detection.
     * 
     * This is useful for custom processing or when working with
     * pre-extracted ankle positions.
     * 
     * @param frameNumber The frame number
     * @param timestampMs The timestamp in milliseconds
     * @param ankleY The Y coordinate of the ankle
     * @param visibility The visibility of the ankle landmark
     * @param side Which leg is being analyzed
     * @return List of detected extrema events (may be empty)
     */
    fun processAnklePosition(
        frameNumber: Long,
        timestampMs: Long,
        ankleY: Float,
        visibility: Float,
        side: BodySide
    ): List<PedalExtremumEvent> {
        if (visibility < config.visibilityThreshold) {
            return emptyList()
        }

        val frameData = FrameData(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            ankleY = ankleY,
            visibility = visibility
        )

        val buffer = if (side == BodySide.LEFT) leftBuffer else rightBuffer
        buffer.addLast(frameData)

        while (buffer.size > config.windowSize * 2) {
            buffer.removeFirst()
        }

        if (buffer.size < config.windowSize) {
            return emptyList()
        }

        return detectExtrema(buffer, side)
    }

    /**
     * Detects extrema in the buffer using sliding window.
     * 
     * The buffer contains recent frames. We examine whether the frame
     * at the center of the window is a local extremum by comparing
     * it to its neighbors on both sides.
     */
    private fun detectExtrema(buffer: ArrayDeque<FrameData>, side: BodySide): List<PedalExtremumEvent> {
        val events = mutableListOf<PedalExtremumEvent>()

        val windowSize = config.windowSize
        if (buffer.size < windowSize) return events

        // The candidate for extremum is at the center of the window
        // We need at least halfWindow frames on each side
        val halfWindow = windowSize / 2
        
        // Look at the center element of the last windowSize elements
        val candidateIndex = buffer.size - 1 - halfWindow
        if (candidateIndex < halfWindow) return events

        val candidate = buffer.elementAt(candidateIndex)

        // Check if candidate is a local maximum Y (BDC) or minimum Y (TDC)
        var isBdc = true
        var isTdc = true

        // Compare with neighbors on both sides
        for (offset in -halfWindow..halfWindow) {
            if (offset == 0) continue
            
            val neighborIndex = candidateIndex + offset
            if (neighborIndex < 0 || neighborIndex >= buffer.size) continue

            val neighbor = buffer.elementAt(neighborIndex)

            // BDC: candidate Y should be > all neighbors (lowest point = max Y in image coords)
            // Use > instead of >= to require a strict maximum
            if (neighbor.ankleY >= candidate.ankleY) {
                isBdc = false
            }

            // TDC: candidate Y should be < all neighbors (highest point = min Y in image coords)
            if (neighbor.ankleY <= candidate.ankleY) {
                isTdc = false
            }
        }

        // Check minimum cycle distance for BDC
        val lastBdcFrame = if (side == BodySide.LEFT) lastLeftBdcFrame else lastRightBdcFrame
        val lastTdcFrame = if (side == BodySide.LEFT) lastLeftTdcFrame else lastRightTdcFrame

        // Use safe comparison to avoid overflow with Long.MIN_VALUE
        val bdcFrameOk = lastBdcFrame == Long.MIN_VALUE || 
            candidate.frameNumber - lastBdcFrame >= config.minCycleFrames
        val tdcFrameOk = lastTdcFrame == Long.MIN_VALUE || 
            candidate.frameNumber - lastTdcFrame >= config.minCycleFrames

        if (isBdc && bdcFrameOk) {
            // Calculate confidence based on how clear the extremum is
            val confidence = calculateExtremumConfidence(buffer, candidateIndex, isBdc = true)

            events.add(PedalExtremumEvent(
                type = PedalExtremum.BDC,
                frameNumber = candidate.frameNumber,
                timestampMs = candidate.timestampMs,
                ankleY = candidate.ankleY,
                side = side,
                confidence = confidence
            ))

            if (side == BodySide.LEFT) {
                lastLeftBdcFrame = candidate.frameNumber
            } else {
                lastRightBdcFrame = candidate.frameNumber
            }
        }

        if (isTdc && tdcFrameOk) {
            val confidence = calculateExtremumConfidence(buffer, candidateIndex, isBdc = false)

            events.add(PedalExtremumEvent(
                type = PedalExtremum.TDC,
                frameNumber = candidate.frameNumber,
                timestampMs = candidate.timestampMs,
                ankleY = candidate.ankleY,
                side = side,
                confidence = confidence
            ))

            if (side == BodySide.LEFT) {
                lastLeftTdcFrame = candidate.frameNumber
            } else {
                lastRightTdcFrame = candidate.frameNumber
            }
        }

        return events
    }

    /**
     * Calculates confidence for an extremum based on the difference from neighbors.
     */
    private fun calculateExtremumConfidence(
        buffer: ArrayDeque<FrameData>,
        candidateIndex: Int,
        isBdc: Boolean
    ): Float {
        val candidate = buffer.elementAt(candidateIndex)
        val halfWindow = config.windowSize / 2

        var totalDiff = 0f
        var count = 0

        for (i in (candidateIndex - halfWindow)..(candidateIndex + halfWindow)) {
            if (i == candidateIndex) continue
            if (i < 0 || i >= buffer.size) continue

            val other = buffer.elementAt(i)
            val diff = if (isBdc) {
                candidate.ankleY - other.ankleY // Should be positive for BDC
            } else {
                other.ankleY - candidate.ankleY // Should be positive for TDC
            }
            totalDiff += diff
            count++
        }

        if (count == 0) return 0.5f

        val avgDiff = totalDiff / count

        // Normalize confidence: small differences = lower confidence
        // Typical ankle movement might be 0.1-0.3 in normalized coords
        val normalizedDiff = (avgDiff / 0.1f).coerceIn(0f, 1f)

        return 0.5f + (normalizedDiff * 0.5f) // Range: 0.5 to 1.0
    }

    /**
     * Resets the detector state, clearing all buffers.
     */
    fun reset() {
        leftBuffer.clear()
        rightBuffer.clear()
        lastLeftBdcFrame = Long.MIN_VALUE
        lastLeftTdcFrame = Long.MIN_VALUE
        lastRightBdcFrame = Long.MIN_VALUE
        lastRightTdcFrame = Long.MIN_VALUE
    }

    /**
     * Resets the detector state for a specific side only.
     */
    fun reset(side: BodySide) {
        when (side) {
            BodySide.LEFT -> {
                leftBuffer.clear()
                lastLeftBdcFrame = Long.MIN_VALUE
                lastLeftTdcFrame = Long.MIN_VALUE
            }
            BodySide.RIGHT -> {
                rightBuffer.clear()
                lastRightBdcFrame = Long.MIN_VALUE
                lastRightTdcFrame = Long.MIN_VALUE
            }
        }
    }

    /**
     * Gets the number of frames currently in the buffer.
     */
    fun getBufferSize(side: BodySide): Int {
        return if (side == BodySide.LEFT) leftBuffer.size else rightBuffer.size
    }

    companion object {
        /**
         * Analyzes a complete sequence of frames and returns all detected extrema.
         * 
         * This is useful for offline analysis of recorded video.
         * 
         * @param frames List of pose frames to analyze
         * @param side Which leg to analyze
         * @param config Detector configuration
         * @return List of all detected extrema events in chronological order
         */
        fun analyzeFrameSequence(
            frames: List<PoseFrame>,
            side: BodySide,
            config: PedalCycleDetectorConfig = PedalCycleDetectorConfig()
        ): List<PedalExtremumEvent> {
            val detector = PedalCycleDetector(config)
            val allEvents = mutableListOf<PedalExtremumEvent>()

            for (frame in frames) {
                allEvents.addAll(detector.processFrame(frame, side))
            }

            return allEvents
        }

        /**
         * Analyzes ankle positions directly without full pose frames.
         * 
         * @param anklePositions List of (frameNumber, timestampMs, ankleY, visibility) tuples
         * @param side Which leg is being analyzed
         * @param config Detector configuration
         * @return List of all detected extrema events
         */
        fun analyzeAnklePositions(
            anklePositions: List<AnklePosition>,
            side: BodySide,
            config: PedalCycleDetectorConfig = PedalCycleDetectorConfig()
        ): List<PedalExtremumEvent> {
            val detector = PedalCycleDetector(config)
            val allEvents = mutableListOf<PedalExtremumEvent>()

            for (pos in anklePositions) {
                allEvents.addAll(
                    detector.processAnklePosition(
                        frameNumber = pos.frameNumber,
                        timestampMs = pos.timestampMs,
                        ankleY = pos.y,
                        visibility = pos.visibility,
                        side = side
                    )
                )
            }

            return allEvents
        }
    }
}

/**
 * Simple data class for ankle position in sequence analysis.
 */
data class AnklePosition(
    val frameNumber: Long,
    val timestampMs: Long,
    val y: Float,
    val visibility: Float = 1.0f
)
