package pt.ineeve.bikefitapp.biomechanics

import pt.ineeve.bikefitapp.pose.PoseFrame
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import pt.ineeve.bikefitapp.calibration.BikeCalibration
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Detects the 3 O'Clock pedal position in the pedal stroke cycle.
 * 
 * The 3 O'Clock position is when the pedal (crank) is horizontal,
 * with the ankle at the same height as the bottom bracket (3 o'clock position only).
 * This is the standard position for KOPS (Knee Over Pedal Spindle) analysis.
 * 
 * Detection Strategy (Crank Geometry):
 * - Calculate crank angle from ankle position relative to bottom bracket
 * - crank_angle = atan2(ankle_y - BB_y, ankle_x - BB_x)
 * - Detect when crank angle is in range [85°, 95°] (3 o'clock position)
 * - Apply filtering to reduce pose detection jitter and outliers
 * - Confidence based on how close angle is to exactly 90°
 */
object ThreeOClockDetector {
    
    /**
     * Configuration for 3 O'Clock detection.
     */
    data class Config(
        /** Minimum crank angle in degrees for 3 O'Clock position */
        val crankAngleMinDegrees: Float = 85f,
        /** Maximum crank angle in degrees for 3 O'Clock position */
        val crankAngleMaxDegrees: Float = 95f,
        /** Minimum visibility threshold for landmarks */
        val visibilityThreshold: Float = 0.5f
    )
    
    /**
     * Represents a detected 3 O'Clock event.
     * 
     * @param frameNumber Frame number where detected
     * @param timestampMs Timestamp in milliseconds
     * @param crankAngleDegrees Calculated crank angle in degrees
     * @param ankleY Y coordinate of foot index (now using foot landmark, kept for backward compatibility)
     * @param hipY Y coordinate of hip
     * @param yDifference Absolute difference between foot and hip Y (for legacy logging)
     * @param side Which leg was analyzed
     * @param confidence Detection confidence (0-1)
     */
    data class ThreeOClockEvent(
        val frameNumber: Long,
        val timestampMs: Long,
        val crankAngleDegrees: Float,
        val ankleY: Float,
        val hipY: Float,
        val yDifference: Float,
        val side: BodySide,
        val confidence: Float
    )
    
    /**
     * Computes raw crank angle for a frame regardless of position.
     * 
     * Does NOT apply filtering or range validation; purely geometric calculation
     * of foot position relative to bottom bracket.
     * 
     * @param frame Pose frame to analyze
     * @param side Which leg to analyze
     * @param calibration Bike calibration with bottom bracket position (required)
     * @return Crank angle in degrees [0, 360), or null if landmarks invalid or missing
     */
    fun computeCrankAngle(
        frame: PoseFrame,
        side: BodySide = BodySide.LEFT,
        calibration: BikeCalibration? = null
    ): Float? {
        // Require calibration for crank angle calculation
        if (calibration?.bottomBracket == null) {
            android.util.Log.d("ThreeOClockDetector", "computeCrankAngle: calibration missing or bottomBracket null: calibration=$calibration")
            return null
        }
        
        val footIndex = if (side == BodySide.LEFT) 
            PoseLandmarkIndex.LEFT_FOOT_INDEX 
        else 
            PoseLandmarkIndex.RIGHT_FOOT_INDEX
        
        val foot = frame.landmarks[footIndex]
        
        // Validate landmark exists and is visible
        if (foot == null) {
            android.util.Log.d("ThreeOClockDetector", "computeCrankAngle: foot is NULL for side $side")
            return null
        }
        if (foot.visibility < 0.1f) {
            android.util.Log.d("ThreeOClockDetector", "computeCrankAngle: foot visibility ${foot.visibility} < 0.1 threshold for side $side")
            return null
        }
        
        // Calculate crank angle using foot position relative to bottom bracket
        val bb = calibration.bottomBracket!!
        val dx = foot.x - bb.x
        val dy = foot.y - bb.y
        android.util.Log.d("ThreeOClockDetector", "computeCrankAngle: side=$side, foot=(${foot.x},${foot.y}), bb=(${bb.x},${bb.y}), dx=$dx, dy=$dy")
        
        // Negate Y because MediaPipe Y increases downward (image space)
        val crankAngleRadians = atan2(-dy.toDouble(), dx.toDouble())
        var crankAngleDegrees = Math.toDegrees(crankAngleRadians).toFloat()
        
        // Normalize angle to [0, 360)
        if (crankAngleDegrees < 0) crankAngleDegrees += 360f
        
        android.util.Log.d("ThreeOClockDetector", "computeCrankAngle SUCCESS: side=$side, angle=$crankAngleDegrees°, foot.vis=${foot.visibility}")
        return crankAngleDegrees
    }
    
    /**
     * Detects if a frame represents approximately the 3 O'Clock position.
     *
     * Uses a pre-computed crank angle from [CrankAngleTracker] (elliptical model) if
     * provided, otherwise falls back to raw atan2 geometry from foot position relative
     * to the bottom bracket.
     *
     * Returns a ThreeOClockEvent if crank angle is within [85°, 95°], null otherwise.
     *
     * @param frame Pose frame to analyze
     * @param side Which leg to analyze
     * @param calibration Bike calibration with bottom bracket position (required)
     * @param config Detection configuration
     * @param modelCrankAngle Pre-computed crank angle from the elliptical model (preferred)
     * @return ThreeOClockEvent if detected, null otherwise
     */
    fun detectAtFrame(
        frame: PoseFrame,
        side: BodySide = BodySide.LEFT,
        calibration: BikeCalibration? = null,
        config: Config = Config(),
        modelCrankAngle: Float? = null
    ): ThreeOClockEvent? {
        // Require calibration for crank angle calculation
        if (calibration?.bottomBracket == null) {
            android.util.Log.d("ThreeOClockDetector", "detectAtFrame: Calibration or bottom bracket is null")
            return null
        }

        val footIndex = if (side == BodySide.LEFT)
            PoseLandmarkIndex.LEFT_FOOT_INDEX
        else
            PoseLandmarkIndex.RIGHT_FOOT_INDEX

        val hipIndex = if (side == BodySide.LEFT)
            PoseLandmarkIndex.LEFT_HIP
        else
            PoseLandmarkIndex.RIGHT_HIP

        val foot = frame.landmarks[footIndex]
        val hip = frame.landmarks[hipIndex]

        // Validate landmarks exist and are visible
        if (foot == null || hip == null) {
            return null
        }
        if (foot.visibility < config.visibilityThreshold || hip.visibility < config.visibilityThreshold) {
            return null
        }

        // Use model angle if provided, otherwise compute via atan2 (raw fallback)
        val crankAngle = modelCrankAngle ?: run {
            val bb = calibration.bottomBracket!!
            val dx = foot.x - bb.x
            val dy = foot.y - bb.y
            val crankAngleRadians = atan2(-dy.toDouble(), dx.toDouble())
            var deg = Math.toDegrees(crankAngleRadians).toFloat()
            if (deg < 0) deg += 360f
            deg
        }

        // Check if crank angle is in the 3 o'clock range
        val distanceToTarget = abs(crankAngle - 90f).let { d ->
            if (d > 180f) 360f - d else d  // Handle wrap-around
        }
        val tolerance = config.crankAngleMaxDegrees - 90f  // 5° tolerance
        val isNearTarget = distanceToTarget <= tolerance

        val yDifference = abs(foot.y - hip.y)

        android.util.Log.d("ThreeOClockDetector",
            "detectAtFrame: frame ${frame.frameNumber}, side $side, crankAngle=${String.format("%.1f", crankAngle)}°, " +
            "distanceFrom90=${String.format("%.1f", distanceToTarget)}°, isNear3OClock=$isNearTarget" +
            if (modelCrankAngle != null) " [model]" else " [atan2 fallback]")

        if (!isNearTarget) {
            return null
        }

        // Confidence: closer to 90° = higher confidence
        val confidence = 1.0f - (distanceToTarget / tolerance).coerceIn(0f, 1f)

        android.util.Log.d("ThreeOClockDetector",
            "detectAtFrame: DETECTED 3 O'Clock at frame ${frame.frameNumber}, side $side, " +
            "crankAngle=${String.format("%.1f", crankAngle)}°, confidence=${String.format("%.2f", confidence)}")

        return ThreeOClockEvent(
            frameNumber = frame.frameNumber,
            timestampMs = frame.timestampMs,
            crankAngleDegrees = crankAngle,
            ankleY = foot.y,
            hipY = hip.y,
            yDifference = yDifference,
            side = side,
            confidence = confidence
        )
    }
    
    /**
     * Resets detector state (useful when restarting video analysis).
     *
     * Kept for API compatibility. The ThreeOClockDetector no longer maintains
     * internal filter state; it consumes pre-computed angles from CrankAngleTracker.
     */
    fun resetAngleFilter() {
        android.util.Log.d("ThreeOClockDetector", "ThreeOClockDetector reset")
    }
    
    /**
     * Finds the best 3 O'Clock frame in a sequence of frames.
     * 
     * Searches through all frames and returns the one with the highest
     * detection confidence.
     * 
     * @param frames List of pose frames to search
     * @param side Which leg to analyze
     * @param calibration Bike calibration with bottom bracket position
     * @param config Detection configuration
     * @return Best ThreeOClockEvent found, or null if none detected
     */
    fun findBestFrame(
        frames: List<PoseFrame>,
        side: BodySide = BodySide.LEFT,
        calibration: BikeCalibration? = null,
        config: Config = Config()
    ): ThreeOClockEvent? {
        return frames
            .mapNotNull { detectAtFrame(it, side, calibration, config) }
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
     * @param calibration Bike calibration with bottom bracket position
     * @param config Detection configuration
     * @return List of all detected ThreeOClockEvents
     */
    fun findAllFrames(
        frames: List<PoseFrame>,
        side: BodySide = BodySide.LEFT,
        calibration: BikeCalibration? = null,
        config: Config = Config()
    ): List<ThreeOClockEvent> {
        return frames.mapNotNull { detectAtFrame(it, side, calibration, config) }
    }
}
