package pt.ineeve.bikefitapp.calibration

/**
 * Types of bike reference points that can be calibrated.
 */
enum class BikeReferencePointType {
    /** Top of the saddle where the rider sits */
    SADDLE_TOP,
    
    /** Center of the bottom bracket (crank axle) */
    BOTTOM_BRACKET,
    
    /** Handlebar grip position */
    HANDLEBAR
}

/**
 * Represents a bike reference point marked by the user.
 * 
 * @param type The type of reference point
 * @param x Normalized x coordinate (0.0 to 1.0, left to right)
 * @param y Normalized y coordinate (0.0 to 1.0, top to bottom)
 */
data class BikeReferencePoint(
    val type: BikeReferencePointType,
    val x: Float,
    val y: Float
) {
    /**
     * Returns the pixel coordinates for a given image size.
     */
    fun toPixels(imageWidth: Int, imageHeight: Int): Pair<Float, Float> {
        return Pair(x * imageWidth, y * imageHeight)
    }
    
    companion object {
        /**
         * Creates a reference point from pixel coordinates.
         */
        fun fromPixels(
            type: BikeReferencePointType,
            pixelX: Float,
            pixelY: Float,
            imageWidth: Int,
            imageHeight: Int
        ): BikeReferencePoint {
            return BikeReferencePoint(
                type = type,
                x = pixelX / imageWidth,
                y = pixelY / imageHeight
            )
        }
    }
}

/**
 * Holds all calibration data for a bike setup.
 * 
 * @param saddleTop Top of the saddle position
 * @param bottomBracket Center of bottom bracket position
 * @param handlebar Handlebar grip position
 * @param timestampMs When the calibration was performed
 */
data class BikeCalibration(
    val saddleTop: BikeReferencePoint? = null,
    val bottomBracket: BikeReferencePoint? = null,
    val handlebar: BikeReferencePoint? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Returns true if all three reference points have been set.
     */
    val isComplete: Boolean
        get() = saddleTop != null && bottomBracket != null && handlebar != null
    
    /**
     * Returns the number of points that have been set.
     */
    val pointCount: Int
        get() = listOfNotNull(saddleTop, bottomBracket, handlebar).size
    
    /**
     * Returns a list of all set reference points.
     */
    fun getPoints(): List<BikeReferencePoint> {
        return listOfNotNull(saddleTop, bottomBracket, handlebar)
    }
    
    /**
     * Returns the next point type that needs to be set, or null if complete.
     */
    fun getNextPointType(): BikeReferencePointType? {
        return when {
            saddleTop == null -> BikeReferencePointType.SADDLE_TOP
            bottomBracket == null -> BikeReferencePointType.BOTTOM_BRACKET
            handlebar == null -> BikeReferencePointType.HANDLEBAR
            else -> null
        }
    }
    
    /**
     * Returns a copy with the given point added/updated.
     */
    fun withPoint(point: BikeReferencePoint): BikeCalibration {
        return when (point.type) {
            BikeReferencePointType.SADDLE_TOP -> copy(saddleTop = point)
            BikeReferencePointType.BOTTOM_BRACKET -> copy(bottomBracket = point)
            BikeReferencePointType.HANDLEBAR -> copy(handlebar = point)
        }
    }
    
    /**
     * Calculates the saddle height relative to bottom bracket.
     * Returns null if either point is not set.
     */
    fun getSaddleHeightRatio(): Float? {
        val saddle = saddleTop ?: return null
        val bb = bottomBracket ?: return null
        return bb.y - saddle.y  // Positive means saddle is above BB
    }
    
    companion object {
        /** Empty calibration with no points set */
        val EMPTY = BikeCalibration()
    }
}

/**
 * State of the calibration process.
 */
sealed class CalibrationState {
    /** Waiting for user to tap saddle top */
    object WaitingForSaddle : CalibrationState()
    
    /** Waiting for user to tap bottom bracket */
    object WaitingForBottomBracket : CalibrationState()
    
    /** Waiting for user to tap handlebar */
    object WaitingForHandlebar : CalibrationState()
    
    /** All points collected, ready to confirm */
    object ReadyToConfirm : CalibrationState()
    
    /** Calibration confirmed and complete */
    data class Confirmed(val calibration: BikeCalibration) : CalibrationState()
    
    /**
     * Returns the instruction text for the current state.
     */
    fun getInstructionText(): String {
        return when (this) {
            is WaitingForSaddle -> "Tap the top of the saddle"
            is WaitingForBottomBracket -> "Tap the center of the bottom bracket"
            is WaitingForHandlebar -> "Tap the handlebar grip"
            is ReadyToConfirm -> "Review and confirm calibration"
            is Confirmed -> "Calibration complete"
        }
    }
    
    /**
     * Returns the current point type being collected.
     */
    fun getCurrentPointType(): BikeReferencePointType? {
        return when (this) {
            is WaitingForSaddle -> BikeReferencePointType.SADDLE_TOP
            is WaitingForBottomBracket -> BikeReferencePointType.BOTTOM_BRACKET
            is WaitingForHandlebar -> BikeReferencePointType.HANDLEBAR
            else -> null
        }
    }
}
