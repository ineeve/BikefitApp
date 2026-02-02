package pt.ineeve.bikefitapp.biomechanics

import pt.ineeve.bikefitapp.pose.Landmark
import pt.ineeve.bikefitapp.pose.PoseFrame
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import pt.ineeve.bikefitapp.pose.PoseResult

/**
 * Result of an ankle angle calculation.
 * 
 * @param angle The ankle plantarflexion angle in degrees. 
 *              0° = neutral (foot perpendicular to shin)
 *              Positive = plantarflexion (foot pointing down, typical 20-30° at BDC)
 *              Negative = dorsiflexion (foot pointing up)
 * @param side Which leg was analyzed
 * @param isValid Whether all required landmarks were visible
 * @param confidence Average visibility of the landmarks used
 */
data class AnkleAngleResult(
    val angle: Float,
    val side: BodySide,
    val isValid: Boolean,
    val confidence: Float
) {
    companion object {
        /**
         * Creates an invalid result when landmarks are not available.
         */
        fun invalid(side: BodySide): AnkleAngleResult {
            return AnkleAngleResult(
                angle = 0f,
                side = side,
                isValid = false,
                confidence = 0f
            )
        }
    }
}

/**
 * Calculates ankle plantarflexion angles from pose landmarks.
 * 
 * The angle is calculated from the vertex angle at the ankle joint formed by:
 * - The knee landmark
 * - The ankle landmark (vertex)
 * - The foot index landmark
 * 
 * The result is converted to plantarflexion: (vertex angle - 90°)
 * - 0° = neutral (foot perpendicular to shin)
 * - Positive values = plantarflexion (foot pointing down)
 * - Negative values = dorsiflexion (foot pointing up)
 * 
 * For cycling, typical plantarflexion at BDC is 20-30°.
 * Values > 35° may indicate Achilles tendon stress risk.
 * 
 * All functions are pure - they take input and return output without
 * modifying any state.
 */
object AnkleAngleCalculator {

    /** Minimum visibility threshold for landmarks to be considered valid */
    const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f

    /**
     * Calculates the ankle angle from a PoseResult.
     * 
     * @param poseResult The pose detection result containing landmarks
     * @param side Which leg to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return AnkleAngleResult with the calculated angle or invalid result
     */
    fun calculateAnkleAngle(
        poseResult: PoseResult,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): AnkleAngleResult {
        if (!poseResult.isValid || poseResult.landmarks.isEmpty()) {
            return AnkleAngleResult.invalid(side)
        }

        return calculateAnkleAngleFromLandmarks(
            poseResult.landmarks,
            side,
            visibilityThreshold
        )
    }

    /**
     * Calculates the ankle angle from a PoseFrame.
     * 
     * @param poseFrame The pose frame containing landmarks
     * @param side Which leg to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return AnkleAngleResult with the calculated angle or invalid result
     */
    fun calculateAnkleAngle(
        poseFrame: PoseFrame,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): AnkleAngleResult {
        if (poseFrame.landmarks.isEmpty()) {
            return AnkleAngleResult.invalid(side)
        }

        return calculateAnkleAngleFromLandmarks(
            poseFrame.landmarks,
            side,
            visibilityThreshold
        )
    }

    /**
     * Calculates the ankle angle from a list of landmarks.
     * 
     * @param landmarks List of 33 pose landmarks
     * @param side Which leg to analyze (LEFT or RIGHT)
     * @param visibilityThreshold Minimum visibility for landmarks to be valid
     * @return AnkleAngleResult with the calculated angle or invalid result
     */
    fun calculateAnkleAngleFromLandmarks(
        landmarks: List<Landmark>,
        side: BodySide,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): AnkleAngleResult {
        if (landmarks.size < PoseLandmarkIndex.LANDMARK_COUNT) {
            return AnkleAngleResult.invalid(side)
        }

        // Get landmark indices based on side
        val kneeIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_KNEE
        } else {
            PoseLandmarkIndex.RIGHT_KNEE
        }
        
        val ankleIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_ANKLE
        } else {
            PoseLandmarkIndex.RIGHT_ANKLE
        }
        
        val footIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_FOOT_INDEX
        } else {
            PoseLandmarkIndex.RIGHT_FOOT_INDEX
        }

        // Get the landmarks
        val knee = landmarks[kneeIndex]
        val ankle = landmarks[ankleIndex]
        val footIndexPoint = landmarks[footIndex]

        // Check visibility
        if (!knee.isVisible(visibilityThreshold) ||
            !ankle.isVisible(visibilityThreshold) ||
            !footIndexPoint.isVisible(visibilityThreshold)) {
            return AnkleAngleResult.invalid(side)
        }

        // Calculate average confidence
        val confidence = (knee.visibility + ankle.visibility + footIndexPoint.visibility) / 3f

        // Calculate vertex angle using Vector2D, then convert to plantarflexion
        val vertexAngle = calculateAngleAtAnkle(knee, ankle, footIndexPoint)
        val plantarflexion = vertexAngle - 90f

        return AnkleAngleResult(
            angle = plantarflexion,
            side = side,
            isValid = true,
            confidence = confidence
        )
    }

    /**
     * Calculates both ankle angles from a PoseResult.
     * 
     * @param poseResult The pose detection result
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Pair of AnkleAngleResults (left, right)
     */
    fun calculateBothAnkleAngles(
        poseResult: PoseResult,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): Pair<AnkleAngleResult, AnkleAngleResult> {
        val leftResult = calculateAnkleAngle(poseResult, BodySide.LEFT, visibilityThreshold)
        val rightResult = calculateAnkleAngle(poseResult, BodySide.RIGHT, visibilityThreshold)
        return Pair(leftResult, rightResult)
    }

    /**
     * Calculates both ankle angles from a PoseFrame.
     * 
     * @param poseFrame The pose frame
     * @param visibilityThreshold Minimum visibility for landmarks
     * @return Pair of AnkleAngleResults (left, right)
     */
    fun calculateBothAnkleAngles(
        poseFrame: PoseFrame,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
    ): Pair<AnkleAngleResult, AnkleAngleResult> {
        val leftResult = calculateAnkleAngle(poseFrame, BodySide.LEFT, visibilityThreshold)
        val rightResult = calculateAnkleAngle(poseFrame, BodySide.RIGHT, visibilityThreshold)
        return Pair(leftResult, rightResult)
    }

    /**
     * Internal function to calculate the angle at the ankle joint.
     * 
     * Uses the Vector2D.angleAtVertex function to calculate the angle
     * formed at the ankle between the knee and foot index.
     * 
     * @param knee The knee landmark
     * @param ankle The ankle landmark (vertex of the angle)
     * @param footIndex The foot index landmark
     * @return The angle in degrees (0-180)
     */
    internal fun calculateAngleAtAnkle(
        knee: Landmark,
        ankle: Landmark,
        footIndex: Landmark
    ): Float {
        val kneePoint = Vector2D(knee.x, knee.y)
        val anklePoint = Vector2D(ankle.x, ankle.y)
        val footIndexPoint = Vector2D(footIndex.x, footIndex.y)

        return Vector2D.angleAtVertex(
            a = kneePoint,
            b = anklePoint,
            c = footIndexPoint
        )
    }

    /**
     * Calculates the ankle plantarflexion from raw coordinate values.
     * 
     * This is a convenience function for direct calculation without
     * creating Landmark objects.
     * 
     * @param kneeX Knee x coordinate
     * @param kneeY Knee y coordinate
     * @param ankleX Ankle x coordinate
     * @param ankleY Ankle y coordinate
     * @param footIndexX Foot index x coordinate
     * @param footIndexY Foot index y coordinate
     * @return The plantarflexion angle in degrees (0° = neutral, positive = plantarflexion)
     */
    fun calculateAnkleAngleFromCoordinates(
        kneeX: Float, kneeY: Float,
        ankleX: Float, ankleY: Float,
        footIndexX: Float, footIndexY: Float
    ): Float {
        val vertexAngle = Vector2D.angleAtVertex(
            a = Vector2D(kneeX, kneeY),
            b = Vector2D(ankleX, ankleY),
            c = Vector2D(footIndexX, footIndexY)
        )
        return vertexAngle - 90f
    }
}
