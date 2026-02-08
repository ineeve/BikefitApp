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
    
    /** Filter state for angle smoothing and outlier rejection (per body side) */
    private val filterStateLeft = CrankAngleFilter.FilterState()
    private val filterStateRight = CrankAngleFilter.FilterState()
    
    /**
     * Configuration for 3 O'Clock detection.
     */
    data class Config(
        /** Minimum crank angle in degrees for 3 O'Clock position */
        val crankAngleMinDegrees: Float = 85f,
        /** Maximum crank angle in degrees for 3 O'Clock position */
        val crankAngleMaxDegrees: Float = 95f,
        /** Minimum visibility threshold for landmarks */
        val visibilityThreshold: Float = 0.5f,
        /** Enable angle filtering and smoothing */
        val enableAngleFiltering: Boolean = true,
        /** Configuration for angle filter */
        val angleFilterConfig: CrankAngleFilter.FilterConfig = CrankAngleFilter.FilterConfig()
    )
    
    /**
     * Represents a detected 3 O'Clock event.
     * 
     * @param frameNumber Frame number where detected
     * @param timestampMs Timestamp in milliseconds
     * @param crankAngleDegrees Calculated crank angle in degrees
     * @param ankleY Y coordinate of ankle
     * @param hipY Y coordinate of hip
     * @param yDifference Absolute difference between ankle and hip Y (for legacy logging)
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
     * Detects if a frame represents approximately the 3 O'Clock position.
     * 
     * Uses crank angle geometry: calculates angle from ankle relative to bottom bracket.
     * Returns a ThreeOClockEvent if crank angle is within [85°, 95°], null otherwise.
     * 
     * @param frame Pose frame to analyze
     * @param side Which leg to analyze
     * @param calibration Bike calibration with bottom bracket position (required)
     * @param config Detection configuration
     * @return ThreeOClockEvent if detected, null otherwise
     */
    fun detectAtFrame(
        frame: PoseFrame,
        side: BodySide = BodySide.LEFT,
        calibration: BikeCalibration? = null,
        config: Config = Config()
    ): ThreeOClockEvent? {
        // Require calibration for crank angle calculation
        if (calibration?.bottomBracket == null) {
            android.util.Log.d("ThreeOClockDetector", "detectAtFrame: Calibration or bottom bracket is null")
            return null
        }
        
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
        
        // Calculate crank angle using ankle position relative to bottom bracket
        // In MediaPipe coordinates: Y increases downward, X increases right
        //
        // Standard crank angle notation:
        // - 0° / 360° = 3 o'clock (right, horizontal)
        // - 90° = 6 o'clock (down, bottom)
        // - 180° = 9 o'clock (left, horizontal)
        // - 270° = 12 o'clock (up, top)
        val bb = calibration.bottomBracket!!
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side - BB position: x=${String.format("%.4f", bb.x)}, y=${String.format("%.4f", bb.y)}")
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side - Ankle position: x=${String.format("%.4f", ankle.x)}, y=${String.format("%.4f", ankle.y)}, visibility=${String.format("%.3f", ankle.visibility)}")
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side - Hip position: x=${String.format("%.4f", hip.x)}, y=${String.format("%.4f", hip.y)}, visibility=${String.format("%.3f", hip.visibility)}")
        
        val dx = ankle.x - bb.x
        val dy = ankle.y - bb.y
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side - Vector from BB to ankle: dx=${String.format("%.4f", dx)}, dy=${String.format("%.4f", dy)}")
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side - Distance from BB: ${String.format("%.4f", kotlin.math.sqrt((dx * dx + dy * dy).toDouble()))}")
        
        // Negate Y because MediaPipe Y increases downward (image space), but crank angles need standard Cartesian orientation
        // This ensures: 3 o'clock (right, +X)=0°, 6 o'clock (down, +Y)=90°, 9 o'clock (left, -X)=180°, 12 o'clock (up, -Y)=270°
        val crankAngleRadians = atan2(-dy.toDouble(), dx.toDouble())
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side - atan2(dy=$dy, dx=$dx) = ${String.format("%.4f", crankAngleRadians)} radians")
        
        var crankAngleDegrees = Math.toDegrees(crankAngleRadians).toFloat()
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side - Before normalization: crankAngleDegrees=${String.format("%.2f", crankAngleDegrees)}°")
        
        // Normalize angle to [0, 360)
        if (crankAngleDegrees < 0) crankAngleDegrees += 360f
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side - After normalization: crankAngleDegrees=${String.format("%.2f", crankAngleDegrees)}°")
        
        // Apply angle filtering if enabled
        var filteredAngle = crankAngleDegrees
        var angleWasFiltered = false
        var filterOutlier = false
        
        if (config.enableAngleFiltering) {
            val filterState = if (side == BodySide.LEFT) filterStateLeft else filterStateRight
            val filterResult = CrankAngleFilter.filterAngle(crankAngleDegrees, filterState, config.angleFilterConfig)
            
            angleWasFiltered = filterResult.isOutlier
            filterOutlier = filterResult.isOutlier
            
            if (filterResult.isValid && filterResult.angle != null) {
                filteredAngle = filterResult.angle
            }
            
            if (angleWasFiltered) {
                val delta = filterResult.angleDelta ?: 0f
                val lastValid = filterState.lastValidAngle ?: 0f
                val smoothed = filterState.smoothedAngle ?: 0f
                android.util.Log.d("ThreeOClockDetector", "detectAtFrame: OUTLIER REJECTED at frame ${frame.frameNumber}, side $side, raw=$crankAngleDegrees°, delta=$delta° (threshold=${config.angleFilterConfig.maxAngleChangePerFrame}°), lastValid=$lastValid°, smoothed=$smoothed°")
                return null  // Skip outlier frames
            }
        }
        
        // For 3 o'clock detection, check if crank angle is in the 3 o'clock range [85°, 95°]
        // Standard crank angle notation:
        // - 0° / 360° = 3 o'clock (right, horizontal)
        // - 90° = 6 o'clock (down, bottom)
        // - 180° = 9 o'clock (left, horizontal)
        // - 270° = 12 o'clock (up, top)
        
        // Check if we're near 90° (horizontal/3 o'clock position)
        var normalizedAngle = filteredAngle
        var distanceToTarget: Float
        
        if (filteredAngle <= 180f) {
            distanceToTarget = abs(filteredAngle - 90f)
        } else {
            distanceToTarget = abs(filteredAngle - 90f)
        }
        
        val tolerance = (config.crankAngleMaxDegrees - 90f)  // 5° tolerance
        val isNearTarget = distanceToTarget <= tolerance
        
        // For legacy logging, also calculate Y difference
        val yDifference = abs(ankle.y - hip.y)
        
        // Log the detected angle
        val filterLog = if (angleWasFiltered) " (filtered from $crankAngleDegrees°)" else ""
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: frame ${frame.frameNumber}, side $side, crankAngle=$filteredAngle°$filterLog, distanceFrom90=$distanceToTarget°, ankle.y=${ankle.y}, hip.y=${hip.y}, yDiff=$yDifference, isNear90=$isNearTarget")
        
        // Check if crank is approximately at 3 o'clock (within ±5° of 90°)
        if (!isNearTarget) {
            android.util.Log.d("ThreeOClockDetector", "detectAtFrame: crankAngle $filteredAngle° not near 3 o'clock (distance=$distanceToTarget° > tolerance=$tolerance°)")
            return null
        }
        
        // Calculate confidence based on how close angle is to exactly 90° (3 o'clock)
        // Maximum confidence at 90°, decreases away from 3 o'clock
        val confidence = 1.0f - (distanceToTarget / tolerance).coerceIn(0f, 1f)
        
        android.util.Log.d("ThreeOClockDetector", "detectAtFrame: DETECTED 3 O'Clock at frame ${frame.frameNumber}, side $side, crankAngle=$filteredAngle°, confidence=$confidence")
        
        return ThreeOClockEvent(
            frameNumber = frame.frameNumber,
            timestampMs = frame.timestampMs,
            crankAngleDegrees = filteredAngle,  // Use filtered angle for analysis
            ankleY = ankle.y,
            hipY = hip.y,
            yDifference = yDifference,
            side = side,
            confidence = confidence
        )
    }
    
    /**
     * Resets angle filter state (useful when restarting video analysis).
     * 
     * Call this when starting a new video or analysis session to clear
     * accumulated filter state from previous data.
     */
    fun resetAngleFilter() {
        CrankAngleFilter.reset(filterStateLeft)
        CrankAngleFilter.reset(filterStateRight)
        android.util.Log.d("ThreeOClockDetector", "Angle filter state reset for both sides")
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
