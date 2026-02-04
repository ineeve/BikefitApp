package pt.ineeve.bikefitapp.biomechanics

import pt.ineeve.bikefitapp.pose.PoseFrame
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import kotlin.math.abs

/**
 * Detects the 3 O'Clock pedal position in the pedal stroke cycle.
 * 
 * The 3 O'Clock position is when the pedal is at the horizontal plane,
 * with the ankle at approximately the same Y-coordinate as the hip.
 * This is the standard position for KOPS (Knee Over Pedal Spindle) analysis.
 * 
 * Detection Strategy:
 * - Monitor ankle Y position relative to hip Y position
 * - The 3 O'Clock position occurs when: |ankle_y - hip_y| < threshold
 * - Also verify the ankle X is to the right (forward) of hip X
 */
object ThreeOClockDetector {
    
    /**
     * Configuration for 3 O'Clock detection.
     */
    data class Config(
        /** Y-coordinate tolerance for horizontal alignment (0-1 normalized) */
        val yToleranceNormalized: Float = 0.23f,
        /** Minimum X offset (ankle should be ahead of hip) */
        val minXOffsetNormalized: Float = 0.01f,
        /** Minimum visibility threshold for landmarks */
        val visibilityThreshold: Float = 0.5f
    )
    
    /**
     * Represents a detected 3 O'Clock event.
     * 
     * @param frameNumber Frame number where detected
     * @param timestampMs Timestamp in milliseconds
     * @param ankleY Y coordinate of ankle
     * @param hipY Y coordinate of hip
     * @param yDifference Absolute difference between ankle and hip Y
     * @param side Which leg was analyzed
     * @param confidence Detection confidence (0-1)
     */
    data class ThreeOClockEvent(
        val frameNumber: Long,
        val timestampMs: Long,
        val ankleY: Float,
        val hipY: Float,
        val yDifference: Float,
        val side: BodySide,
        val confidence: Float
    )
    
    /**
     * Detects if a frame represents approximately the 3 O'Clock position.
     * 
     * Returns a ThreeOClockEvent if detected, null otherwise.
     * 
     * @param frame Pose frame to analyze
     * @param side Which leg to analyze
     * @param config Detection configuration
     * @return ThreeOClockEvent if detected, null otherwise
     */
    fun detectAtFrame(
        frame: PoseFrame,
        side: BodySide = BodySide.LEFT,
        config: Config = Config()
    ): ThreeOClockEvent? {
        val ankleIndex = if (side == BodySide.LEFT) 
            PoseLandmarkIndex.LEFT_ANKLE 
        else 
            PoseLandmarkIndex.RIGHT_ANKLE
        
        val hipIndex = if (side == BodySide.LEFT) 
            PoseLandmarkIndex.LEFT_HIP 
        else 
            PoseLandmarkIndex.RIGHT_HIP
        
        val ankle = frame.landmarks[ankleIndex]
        val hip = frame.landmarks[hipIndex]
        
        // Validate landmarks exist and are visible
        if (ankle == null || hip == null) {
            android.util.Log.d("ThreeOClockDetector", "detectAtFrame: ankle or hip is null for frame ${frame.frameNumber}, side $side")
            return null
        }
        if (ankle.visibility < config.visibilityThreshold) {
            android.util.Log.d("ThreeOClockDetector", "detectAtFrame: ankle visibility ${ankle.visibility} < threshold ${config.visibilityThreshold} at frame ${frame.frameNumber}, side $side")
            return null
        }
        if (hip.visibility < config.visibilityThreshold) {
            android.util.Log.d("ThreeOClockDetector", "detectAtFrame: hip visibility ${hip.visibility} < threshold ${config.visibilityThreshold} at frame ${frame.frameNumber}, side $side")
            return null
        }
        
        // Check if ankle and hip are approximately at same height (3 O'Clock position)
        val yDifference = abs(ankle.y - hip.y)
        
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side, ankle.y=${ankle.y}, hip.y=${hip.y}, yDiff=$yDifference, tolerance=${config.yToleranceNormalized}")
        
        if (yDifference > config.yToleranceNormalized) {
            android.util.Log.d("ThreeOClockDetector", "detectAtFrame: yDifference $yDifference > tolerance ${config.yToleranceNormalized}")
            return null
        }
        
        // Verify ankle is to the right (forward) of hip for this position
        val xDifference = ankle.x - hip.x
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: xDifference=$xDifference, minRequired=${config.minXOffsetNormalized}")
        if (xDifference < config.minXOffsetNormalized) {
            android.util.Log.d("ThreeOClockDetector", "detectAtFrame: xDifference $xDifference < minRequired ${config.minXOffsetNormalized}")
            return null
        }
        
        // Calculate confidence based on how well aligned the ankle is
        // Lower difference = higher confidence
        val confidence = 1.0f - (yDifference / config.yToleranceNormalized).coerceIn(0f, 1f)
        
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: DETECTED 3 O'Clock at frame ${frame.frameNumber}, side $side, confidence=$confidence")
        
        return ThreeOClockEvent(
            frameNumber = frame.frameNumber,
            timestampMs = frame.timestampMs,
            ankleY = ankle.y,
            hipY = hip.y,
            yDifference = yDifference,
            side = side,
            confidence = confidence
        )
    }
    
    /**
     * Finds the best 3 O'Clock frame in a sequence of frames.
     * 
     * Searches through all frames and returns the one with the highest
     * detection confidence.
     * 
     * @param frames List of pose frames to search
     * @param side Which leg to analyze
     * @param config Detection configuration
     * @return Best ThreeOClockEvent found, or null if none detected
     */
    fun findBestFrame(
        frames: List<PoseFrame>,
        side: BodySide = BodySide.LEFT,
        config: Config = Config()
    ): ThreeOClockEvent? {
        return frames
            .mapNotNull { detectAtFrame(it, side, config) }
            .maxByOrNull { it.confidence }
    }
    
    /**
     * Finds all 3 O'Clock positions in a sequence of frames.
     * 
     * Returns all detected 3 O'Clock events, useful for capturing multiple
     * instances across different pedal cycles.
     * 
     * @param frames List of pose frames to search
     * @param side Which leg to analyze
     * @param config Detection configuration
     * @return List of all detected ThreeOClockEvents
     */
    fun findAllFrames(
        frames: List<PoseFrame>,
        side: BodySide = BodySide.LEFT,
        config: Config = Config()
    ): List<ThreeOClockEvent> {
        return frames.mapNotNull { detectAtFrame(it, side, config) }
    }
}
