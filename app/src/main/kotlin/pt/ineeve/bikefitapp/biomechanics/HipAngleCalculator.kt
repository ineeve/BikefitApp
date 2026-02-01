package pt.ineeve.bikefitapp.biomechanics

import pt.ineeve.bikefitapp.pose.Landmark
import pt.ineeve.bikefitapp.pose.PoseFrame
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import pt.ineeve.bikefitapp.pose.PoseResult

/**
 * Result of a hip angle calculation.
 * 
 * The hip angle is measured at the hip joint, formed by:
 * - The shoulder (torso reference)
 * - The hip (vertex of the angle)
 * - The knee (leg reference)
 * 
 * @param angle The hip angle in degrees (0-180). A more upright position has a larger angle.
 * @param side Which leg was analyzed
 * @param isValid Whether all required landmarks were visible
 * @param confidence Average visibility of the landmarks used
 */
data class HipAngleResult(
    val angle: Float,
    val side: BodySide,
    val isValid: Boolean,
    val confidence: Float
) {
    companion object {
        /**
         * Creates an invalid result when landmarks are not available.
         */
        fun invalid(side: BodySide): HipAngleResult {
            return HipAngleResult(
                angle = 0f,
                side = side,
                isValid = false,
                confidence = 0f
            )
        }
    }
}

/**
 * Calculates hip angles from pose landmarks.
 * 
 * The hip angle is measured as the angle at the hip joint formed by:
 * - The shoulder landmark (torso reference)
 * - The hip landmark (vertex of the angle)
 * - The knee landmark (leg reference)
 * 
 * This angle represents how open or closed the hip is relative to the torso.
 * 
 * For cycling:
 * - A more aero position has a smaller hip angle (torso bent forward)
 * - A more upright position has a larger hip angle
 * - Typical road cycling hip angles range from 40-60 degrees at the top of the pedal stroke
 * 
 * All functions are pure - they take input and return output without
 * modifying any state.
 */
object HipAngleCalculator {

    /** Minimum visibility threshold for landmarks to be considered valid */
    const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f

    /**
     * Calculates the hip angle from a PoseResult.
     * 
     * @param poseResult The pose detection result containing landmarks
     * @param side Which side to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return HipAngleResult with the calculated angle or invalid result
     */
    fun calculateHipAngle(
        poseResult: PoseResult,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): HipAngleResult {
        if (!poseResult.isValid || poseResult.landmarks.isEmpty()) {
            return HipAngleResult.invalid(side)
        }

        return calculateHipAngleFromLandmarks(
            poseResult.landmarks,
            side,
            visibilityThreshold
        )
    }

    /**
     * Calculates the hip angle from a PoseFrame.
     * 
     * @param poseFrame The pose frame containing landmarks
     * @param side Which side to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return HipAngleResult with the calculated angle or invalid result
     */
    fun calculateHipAngle(
        poseFrame: PoseFrame,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): HipAngleResult {
        if (poseFrame.landmarks.isEmpty()) {
            return HipAngleResult.invalid(side)
        }

        return calculateHipAngleFromLandmarks(
            poseFrame.landmarks,
            side,
            visibilityThreshold
        )
    }

    /**
     * Calculates the hip angle from a list of landmarks.
     * 
     * @param landmarks List of 33 pose landmarks
     * @param side Which side to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return HipAngleResult with the calculated angle or invalid result
     */
    fun calculateHipAngleFromLandmarks(
        landmarks: List<Landmark>,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): HipAngleResult {
        if (landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return HipAngleResult.invalid(side)
        }

        // Get landmark indices based on side
        val shoulderIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_SHOULDER
        } else {
            PoseLandmarkIndex.RIGHT_SHOULDER
        }

        val hipIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }

        val kneeIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_KNEE
        } else {
            PoseLandmarkIndex.RIGHT_KNEE
        }

        // Get the landmarks
        val shoulder = landmarks[shoulderIndex]
        val hip = landmarks[hipIndex]
        val knee = landmarks[kneeIndex]

        // Check visibility
        if (!shoulder.isVisible(visibilityThreshold) ||
            !hip.isVisible(visibilityThreshold) ||
            !knee.isVisible(visibilityThreshold)) {
            return HipAngleResult.invalid(side)
        }

        // Calculate average confidence
        val confidence = (shoulder.visibility + hip.visibility + knee.visibility) / 3f

        // Calculate angle using Vector2D
        val angle = calculateAngleAtHip(shoulder, hip, knee)

        return HipAngleResult(
            angle = angle,
            side = side,
            isValid = true,
            confidence = confidence
        )
    }

    /**
     * Calculates both hip angles from a PoseResult.
     * 
     * @param poseResult The pose detection result
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Pair of HipAngleResults (left, right)
     */
    fun calculateBothHipAngles(
        poseResult: PoseResult,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): Pair<HipAngleResult, HipAngleResult> {
        val leftResult = calculateHipAngle(poseResult, BodySide.LEFT, visibilityThreshold)
        val rightResult = calculateHipAngle(poseResult, BodySide.RIGHT, visibilityThreshold)
        return Pair(leftResult, rightResult)
    }

    /**
     * Calculates both hip angles from a PoseFrame.
     * 
     * @param poseFrame The pose frame
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Pair of HipAngleResults (left, right)
     */
    fun calculateBothHipAngles(
        poseFrame: PoseFrame,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): Pair<HipAngleResult, HipAngleResult> {
        val leftResult = calculateHipAngle(poseFrame, BodySide.LEFT, visibilityThreshold)
        val rightResult = calculateHipAngle(poseFrame, BodySide.RIGHT, visibilityThreshold)
        return Pair(leftResult, rightResult)
    }

    /**
     * Internal function to calculate the angle at the hip joint.
     * 
     * Uses the Vector2D.angleAtVertex function to calculate the angle
     * formed at the hip between the shoulder and knee.
     * 
     * @param shoulder The shoulder landmark
     * @param hip The hip landmark (vertex of the angle)
     * @param knee The knee landmark
     * @return The angle in degrees (0-180)
     */
    internal fun calculateAngleAtHip(
        shoulder: Landmark,
        hip: Landmark,
        knee: Landmark
    ): Float {
        val shoulderPoint = Vector2D(shoulder.x, shoulder.y)
        val hipPoint = Vector2D(hip.x, hip.y)
        val kneePoint = Vector2D(knee.x, knee.y)

        return Vector2D.angleAtVertex(
            a = shoulderPoint,
            b = hipPoint,
            c = kneePoint
        )
    }

    /**
     * Calculates the hip angle from raw coordinate values.
     * 
     * This is a convenience function for direct calculation without
     * creating Landmark objects.
     * 
     * @param shoulderX Shoulder x coordinate
     * @param shoulderY Shoulder y coordinate
     * @param hipX Hip x coordinate
     * @param hipY Hip y coordinate
     * @param kneeX Knee x coordinate
     * @param kneeY Knee y coordinate
     * @return The hip angle in degrees (0-180)
     */
    fun calculateHipAngleFromCoordinates(
        shoulderX: Float, shoulderY: Float,
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float
    ): Float {
        return Vector2D.angleAtVertex(
            a = Vector2D(shoulderX, shoulderY),
            b = Vector2D(hipX, hipY),
            c = Vector2D(kneeX, kneeY)
        )
    }

    /**
     * Calculates the hip angle using midpoints of shoulders and hips.
     * 
     * This method uses:
     * - Torso vector: shoulder midpoint → hip midpoint
     * - Femur vector: hip midpoint → knee midpoint
     * 
     * This provides a more stable measurement compared to single-side
     * landmarks, especially when the cyclist is viewed from an angle.
     * 
     * @param landmarks List of 33 pose landmarks
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return HipAngleResult with the calculated angle or invalid result
     */
    fun calculateHipAngleFromMidpoints(
        landmarks: List<Landmark>,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): HipAngleResult {
        if (landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return HipAngleResult.invalid(BodySide.LEFT)
        }

        // Get all required landmarks
        val leftShoulder = landmarks[PoseLandmarkIndex.LEFT_SHOULDER]
        val rightShoulder = landmarks[PoseLandmarkIndex.RIGHT_SHOULDER]
        val leftHip = landmarks[PoseLandmarkIndex.LEFT_HIP]
        val rightHip = landmarks[PoseLandmarkIndex.RIGHT_HIP]
        val leftKnee = landmarks[PoseLandmarkIndex.LEFT_KNEE]
        val rightKnee = landmarks[PoseLandmarkIndex.RIGHT_KNEE]

        // Check visibility of all landmarks
        if (!leftShoulder.isVisible(visibilityThreshold) ||
            !rightShoulder.isVisible(visibilityThreshold) ||
            !leftHip.isVisible(visibilityThreshold) ||
            !rightHip.isVisible(visibilityThreshold) ||
            !leftKnee.isVisible(visibilityThreshold) ||
            !rightKnee.isVisible(visibilityThreshold)) {
            return HipAngleResult.invalid(BodySide.LEFT)
        }

        // Calculate midpoints
        val shoulderMidpoint = Vector2D(
            (leftShoulder.x + rightShoulder.x) / 2f,
            (leftShoulder.y + rightShoulder.y) / 2f
        )

        val hipMidpoint = Vector2D(
            (leftHip.x + rightHip.x) / 2f,
            (leftHip.y + rightHip.y) / 2f
        )

        val kneeMidpoint = Vector2D(
            (leftKnee.x + rightKnee.x) / 2f,
            (leftKnee.y + rightKnee.y) / 2f
        )

        // Calculate angle at hip midpoint
        val angle = Vector2D.angleAtVertex(
            a = shoulderMidpoint,
            b = hipMidpoint,
            c = kneeMidpoint
        )

        // Calculate average confidence
        val confidence = (leftShoulder.visibility + rightShoulder.visibility +
                         leftHip.visibility + rightHip.visibility +
                         leftKnee.visibility + rightKnee.visibility) / 6f

        return HipAngleResult(
            angle = angle,
            side = BodySide.LEFT, // Using LEFT as convention for midpoint calculations
            isValid = true,
            confidence = confidence
        )
    }

    /**
     * Calculates the hip angle from a PoseResult using midpoints.
     * 
     * @param poseResult The pose detection result
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return HipAngleResult with the calculated angle or invalid result
     */
    fun calculateHipAngleFromMidpoints(
        poseResult: PoseResult,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): HipAngleResult {
        if (!poseResult.isValid || poseResult.landmarks.isEmpty()) {
            return HipAngleResult.invalid(BodySide.LEFT)
        }

        return calculateHipAngleFromMidpoints(
            poseResult.landmarks,
            visibilityThreshold
        )
    }

    /**
     * Calculates the hip angle from a PoseFrame using midpoints.
     * 
     * @param poseFrame The pose frame
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return HipAngleResult with the calculated angle or invalid result
     */
    fun calculateHipAngleFromMidpoints(
        poseFrame: PoseFrame,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): HipAngleResult {
        if (poseFrame.landmarks.isEmpty()) {
            return HipAngleResult.invalid(BodySide.LEFT)
        }

        return calculateHipAngleFromMidpoints(
            poseFrame.landmarks,
            visibilityThreshold
        )
    }
}
