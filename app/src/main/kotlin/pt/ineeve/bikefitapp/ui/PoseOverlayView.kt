package pt.ineeve.bikefitapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.atan2
import pt.ineeve.bikefitapp.pose.Landmark
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import pt.ineeve.bikefitapp.pose.PoseResult

/**
 * Types of angles tracked for bike fit analysis.
 * Each type has a distinct color for visualization.
 */
enum class AngleType {
    KNEE,
    HIP,
    ANKLE,
    TORSO
}

/**
 * Represents an angle to display on the overlay.
 * 
 * @param angle The angle value in degrees
 * @param landmarkIndex The landmark index where the angle is centered (vertex of the angle)
 * @param fromLandmarkIndex The first adjacent landmark index (start of first ray)
 * @param toLandmarkIndex The second adjacent landmark index (end of second ray), or -1 for horizontal reference (torso)
 * @param angleType The type of angle for color coding
 * @param isValid Whether the angle calculation was valid
 * @param label Optional label for the angle (e.g., "L Knee", "R Knee")
 * @param customVertexX Optional custom vertex X coordinate (normalized 0-1) for angles at line intersections
 * @param customVertexY Optional custom vertex Y coordinate (normalized 0-1) for angles at line intersections
 */
data class AngleDisplay(
    val angle: Float,
    val landmarkIndex: Int,
    val fromLandmarkIndex: Int = -1,
    val toLandmarkIndex: Int = -1,
    val angleType: AngleType = AngleType.KNEE,
    val isValid: Boolean = true,
    val label: String = "",
    val customVertexX: Float? = null,
    val customVertexY: Float? = null
) {
    /** Whether this angle has geometric arc data for drawing */
    val hasArcData: Boolean
        get() = fromLandmarkIndex >= 0
    
    /** Whether this angle uses a custom vertex position (not a landmark) */
    val hasCustomVertex: Boolean
        get() = customVertexX != null && customVertexY != null
    
    companion object {
        fun invalid(landmarkIndex: Int, label: String = "", angleType: AngleType = AngleType.KNEE): AngleDisplay {
            return AngleDisplay(0f, landmarkIndex, angleType = angleType, isValid = false, label = label)
        }
    }
}

/**
 * Custom View that renders pose skeleton overlay on top of camera preview.
 * 
 * This view draws:
 * - Landmark points as colored circles
 * - Skeleton lines connecting joints
 * 
 * The view handles coordinate transformation from normalized MediaPipe
 * coordinates (0-1) to view coordinates, accounting for camera preview
 * scaling and mirroring.
 * 
 * Usage:
 * ```xml
 * <pt.ineeve.bikefitapp.ui.PoseOverlayView
 *     android:id="@+id/pose_overlay"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent" />
 * ```
 * 
 * ```kotlin
 * val overlay = findViewById<PoseOverlayView>(R.id.pose_overlay)
 * overlay.setImageSourceInfo(imageWidth, imageHeight, isFrontCamera)
 * overlay.updatePose(poseResult)
 * ```
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ==================== Configuration ====================
    
    /** Radius of landmark circles in pixels */
    var landmarkRadius: Float = DEFAULT_LANDMARK_RADIUS
        set(value) {
            field = value
            invalidate()
        }
    
    /** Stroke width of skeleton lines in pixels */
    var skeletonStrokeWidth: Float = DEFAULT_SKELETON_STROKE_WIDTH
        set(value) {
            field = value
            skeletonPaint.strokeWidth = value
            invalidate()
        }
    
    /** Color for landmark points */
    var landmarkColor: Int = DEFAULT_LANDMARK_COLOR
        set(value) {
            field = value
            landmarkPaint.color = value
            invalidate()
        }
    
    /** Color for skeleton lines */
    var skeletonColor: Int = DEFAULT_SKELETON_COLOR
        set(value) {
            field = value
            skeletonPaint.color = value
            invalidate()
        }
    
    /** Minimum visibility threshold for drawing landmarks */
    var visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD
        set(value) {
            field = value
            invalidate()
        }
    
    /** Whether to mirror the skeleton horizontally (for front camera) */
    var isMirrored: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Whether to show only bike-fit relevant landmarks */
    var showBikeFitLandmarksOnly: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Whether to show angle values */
    var showAngles: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Text size for angle display */
    var angleTextSize: Float = DEFAULT_ANGLE_TEXT_SIZE
        set(value) {
            field = value
            angleTextPaint.textSize = value
            invalidate()
        }

    /** Color for angle text */
    var angleTextColor: Int = DEFAULT_ANGLE_TEXT_COLOR
        set(value) {
            field = value
            angleTextPaint.color = value
            invalidate()
        }

    // ==================== Paint Objects ====================
    
    private val landmarkPaint = Paint().apply {
        color = DEFAULT_LANDMARK_COLOR
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val skeletonPaint = Paint().apply {
        color = DEFAULT_SKELETON_COLOR
        style = Paint.Style.STROKE
        strokeWidth = DEFAULT_SKELETON_STROKE_WIDTH
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    
    private val lowConfidencePaint = Paint().apply {
        color = LOW_CONFIDENCE_COLOR
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val angleTextPaint = Paint().apply {
        color = DEFAULT_ANGLE_TEXT_COLOR
        textSize = DEFAULT_ANGLE_TEXT_SIZE
        textAlign = Paint.Align.LEFT
        isAntiAlias = true
        isFakeBoldText = true
    }
    
    private val angleBackgroundPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    /** Paint for arc fill (semi-transparent) */
    private val arcFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    /** Paint for arc stroke/rays */
    private val arcStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    
    /** RectF for drawing arcs */
    private val arcRect = RectF()

    /** View scale type for mapping coordinates */
    var scaleType: ViewCoordinateMapper.ScaleType 
        get() = mapper.scaleType
        set(value) {
            mapper.scaleType = value
            invalidate()
        }

    // ==================== State ====================
    
    private var currentPose: PoseResult? = null
    private val mapper = ViewCoordinateMapper()
    
    /** Cached transformed coordinates */
    private val transformedLandmarks = mutableMapOf<Int, PointF>()
    
    /** Angles to display on the overlay */
    private val anglesToDisplay = mutableListOf<AngleDisplay>()
    
    /** Rect for drawing angle backgrounds */
    private val angleRect = RectF()

    // ==================== Public API ====================
    
    /**
     * Sets the source image dimensions for coordinate transformation.
     * 
     * Call this when the camera starts or resolution changes.
     * 
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param isFrontCamera Whether the front camera is being used (enables mirroring)
     */
    fun setImageSourceInfo(width: Int, height: Int, isFrontCamera: Boolean = false) {
        if (mapper.setDimensions(width, height, getWidth(), getHeight(), isFrontCamera)) {
            transformedLandmarks.clear()
            invalidate()
        }
    }
    
    /**
     * Updates the pose to render.
     * 
     * This triggers a redraw of the overlay. Call this each time
     * a new pose is detected from the camera frame.
     * 
     * @param pose The pose result to render, or null to clear
     */
    fun updatePose(pose: PoseResult?) {
        currentPose = pose
        transformedLandmarks.clear()
        invalidate()
    }
    
    /**
     * Updates the angles to display on the overlay.
     * 
     * Call this each frame with the calculated knee angles.
     * Angles are displayed near their corresponding landmark positions.
     * 
     * @param angles List of angles to display
     */
    fun updateAngles(angles: List<AngleDisplay>) {
        anglesToDisplay.clear()
        anglesToDisplay.addAll(angles.filter { it.isValid })
        invalidate()
    }
    
    /**
     * Updates a single angle to display.
     * 
     * @param angle The angle to display
     */
    fun updateAngle(angle: AngleDisplay) {
        updateAngles(listOf(angle))
    }
    
    /**
     * Clears all displayed angles.
     */
    fun clearAngles() {
        anglesToDisplay.clear()
        invalidate()
    }
    
    /**
     * Clears the current pose overlay.
     */
    fun clear() {
        currentPose = null
        transformedLandmarks.clear()
        anglesToDisplay.clear()
        invalidate()
    }
    
    /**
     * Returns true if a valid pose is currently being displayed.
     */
    fun hasPose(): Boolean = currentPose?.isValid == true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mapper.setDimensions(mapper.imageWidth, mapper.imageHeight, w, h, mapper.isMirrored)
        transformedLandmarks.clear()
    }

    // ==================== Drawing ====================
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val pose = currentPose ?: return
        if (!pose.isValid || pose.landmarks.isEmpty()) return
        
        // Transform all landmarks to view coordinates
        transformLandmarks(pose)
        
        // Draw skeleton lines first (so landmarks are on top)
        drawSkeleton(canvas)
        
        // Draw landmark points
        drawLandmarks(canvas, pose)
        
        // Draw angle values
        if (showAngles) {
            drawAngles(canvas)
        }
    }
    
    /**
     * Transforms all landmarks from normalized coordinates to view coordinates.
     */
    private fun transformLandmarks(pose: PoseResult) {
        if (transformedLandmarks.isNotEmpty()) return // Already transformed
        
        pose.landmarks.forEachIndexed { index, landmark ->
            val point = transformCoordinates(landmark)
            transformedLandmarks[index] = point
        }
    }
    
    /**
     * Transforms normalized X, Y coordinates to view coordinates.
     * Used for custom vertex positions that aren't at landmarks.
     */
    private fun transformPoint(normalizedX: Float, normalizedY: Float): PointF {
        return mapper.mapToView(normalizedX, normalizedY)
    }
    
    /**
     * Transforms a landmark from normalized coordinates to view coordinates.
     * Handles proper scaling and cropping to match PreviewView's FILL_CENTER behavior.
     */
    private fun transformCoordinates(landmark: Landmark): PointF {
        return mapper.mapToView(landmark.x, landmark.y)
    }
    
    /**
     * Draws all skeleton lines connecting joints.
     */
    private fun drawSkeleton(canvas: Canvas) {
        val connections = if (showBikeFitLandmarksOnly) {
            BIKE_FIT_SKELETON_CONNECTIONS
        } else {
            SKELETON_CONNECTIONS
        }
        
        for ((startIndex, endIndex) in connections) {
            val start = transformedLandmarks[startIndex] ?: continue
            val end = transformedLandmarks[endIndex] ?: continue
            
            val pose = currentPose ?: continue
            val startLandmark = pose.landmarks.getOrNull(startIndex) ?: continue
            val endLandmark = pose.landmarks.getOrNull(endIndex) ?: continue
            
            // Only draw if both landmarks are visible
            if (startLandmark.visibility < visibilityThreshold ||
                endLandmark.visibility < visibilityThreshold) {
                continue
            }
            
            canvas.drawLine(start.x, start.y, end.x, end.y, skeletonPaint)
        }
    }
    
    /**
     * Draws all landmark points.
     */
    private fun drawLandmarks(canvas: Canvas, pose: PoseResult) {
        val indicesToDraw = if (showBikeFitLandmarksOnly) {
            BIKE_FIT_LANDMARK_INDICES
        } else {
            (0 until PoseLandmarkIndex.LANDMARK_COUNT).toList()
        }
        
        for (index in indicesToDraw) {
            val point = transformedLandmarks[index] ?: continue
            val landmark = pose.landmarks.getOrNull(index) ?: continue
            
            if (landmark.visibility < visibilityThreshold) continue
            
            // Use different paint based on confidence
            val paint = if (landmark.visibility >= HIGH_CONFIDENCE_THRESHOLD) {
                landmarkPaint
            } else {
                lowConfidencePaint
            }
            
            canvas.drawCircle(point.x, point.y, landmarkRadius, paint)
        }
    }
    
    /**
     * Draws angle values near their corresponding landmarks.
     */
    private fun drawAngles(canvas: Canvas) {
        for (angleDisplay in anglesToDisplay) {
            val point = transformedLandmarks[angleDisplay.landmarkIndex] ?: continue
            // Draw geometric arc first (behind text)
            if (angleDisplay.hasArcData) {
                drawAngleArc(canvas, angleDisplay)
            }
            drawAngleText(canvas, point, angleDisplay)
        }
    }
    
    /**
     * Gets the color for a specific angle type.
     */
    private fun getAngleColor(angleType: AngleType): Int {
        return when (angleType) {
            AngleType.KNEE -> KNEE_ANGLE_COLOR
            AngleType.HIP -> HIP_ANGLE_COLOR
            AngleType.ANKLE -> ANKLE_ANGLE_COLOR
            AngleType.TORSO -> TORSO_ANGLE_COLOR
        }
    }
    
    /**
     * Draws a geometric arc visualization for an angle.
     * 
     * The arc is drawn at the vertex (landmarkIndex or custom vertex) representing the actual measured angle.
     * For hip angles, we draw the anterior (front) angle which is what the calculator returns.
     * For torso angles, we draw from horizontal to the torso direction on the left side.
     * For ankle angles, the vertex may be at a line intersection point (custom vertex).
     */
    private fun drawAngleArc(canvas: Canvas, angleDisplay: AngleDisplay) {
        // Get the vertex - either from custom coordinates or from the landmark
        val vertex: PointF = if (angleDisplay.hasCustomVertex) {
            // Transform custom vertex coordinates from normalized to view coordinates
            transformPoint(angleDisplay.customVertexX!!, angleDisplay.customVertexY!!)
        } else {
            transformedLandmarks[angleDisplay.landmarkIndex] ?: return
        }
        val fromPoint = transformedLandmarks[angleDisplay.fromLandmarkIndex] ?: return
        
        // Use different radius for torso to avoid overlap with hip arc
        val radius = if (angleDisplay.angleType == AngleType.TORSO) TORSO_ARC_RADIUS else ARC_RADIUS
        
        // Get base color for this angle type
        val baseColor = getAngleColor(angleDisplay.angleType)
        
        // Set up arc fill paint (semi-transparent)
        arcFillPaint.color = Color.argb(
            ARC_ALPHA,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
        
        // Set up arc stroke paint
        arcStrokePaint.color = Color.argb(
            ARC_STROKE_ALPHA,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
        
        // Calculate angles based on type
        val startAngle: Float
        val sweepAngle: Float
        val ray1Angle: Float
        val ray2Angle: Float
        
        when (angleDisplay.angleType) {
            AngleType.TORSO -> {
                // Torso: angle from horizontal to the hip-shoulder segment
                // Calculate the actual direction from hip to shoulder
                val hipToShoulderAngle = Math.toDegrees(
                    atan2(
                        (fromPoint.y - vertex.y).toDouble(),
                        (fromPoint.x - vertex.x).toDouble()
                    )
                ).toFloat()
                
                // Start from horizontal left (180°) and sweep to the shoulder direction
                startAngle = 180f
                // Calculate sweep from horizontal to the shoulder direction
                var sweep = hipToShoulderAngle - 180f
                // Normalize to sweep the shorter arc
                while (sweep > 180) sweep -= 360
                while (sweep < -180) sweep += 360
                sweepAngle = sweep
                ray1Angle = 180f  // Horizontal left
                ray2Angle = hipToShoulderAngle  // Toward the shoulder
            }
            AngleType.HIP -> {
                // Hip: the returned angle is the anterior (front) angle
                // We want to show the angle between thigh and torso at the front of the body
                // Calculate direction from hip to knee (thigh)
                val toPoint = transformedLandmarks[angleDisplay.toLandmarkIndex] ?: return
                val thighAngle = Math.toDegrees(
                    atan2(
                        (toPoint.y - vertex.y).toDouble(),
                        (toPoint.x - vertex.x).toDouble()
                    )
                ).toFloat()
                
                // Start from thigh direction and sweep the actual hip angle value
                startAngle = thighAngle
                // Sweep toward the torso (anterior angle is at the front)
                sweepAngle = -angleDisplay.angle  // Negative to sweep toward front of body
                ray1Angle = thighAngle
                ray2Angle = thighAngle - angleDisplay.angle
            }
            AngleType.ANKLE -> {
                // Ankle: plantarflexion angle relative to horizontal
                // The angle value is: 0° = foot parallel to ground, + = toes down, - = toes up
                // Calculate direction from vertex (intersection) to foot
                val toPoint = transformedLandmarks[angleDisplay.toLandmarkIndex] ?: return
                val footAngle = Math.toDegrees(
                    atan2(
                        (toPoint.y - vertex.y).toDouble(),
                        (toPoint.x - vertex.x).toDouble()
                    )
                ).toFloat()
                
                // Draw from horizontal to foot direction
                // Horizontal reference depends on which way the foot is pointing
                // If foot is pointing generally right (0° ± 90°), use 0° horizontal
                // If foot is pointing generally left (180° ± 90°), use 180° horizontal
                val horizontalRef = if (kotlin.math.abs(footAngle) < 90f || kotlin.math.abs(footAngle) > 270f) 0f else 180f
                
                startAngle = horizontalRef
                var sweep = footAngle - horizontalRef
                // Normalize to draw the shorter arc
                while (sweep > 180) sweep -= 360
                while (sweep < -180) sweep += 360
                sweepAngle = sweep
                ray1Angle = horizontalRef
                ray2Angle = footAngle
            }
            AngleType.KNEE -> {
                // Knee: use the raw vertex angle calculation
                val toPoint = transformedLandmarks[angleDisplay.toLandmarkIndex] ?: return
                
                val fromAngle = Math.toDegrees(
                    atan2(
                        (fromPoint.y - vertex.y).toDouble(),
                        (fromPoint.x - vertex.x).toDouble()
                    )
                ).toFloat()
                
                val toAngle = Math.toDegrees(
                    atan2(
                        (toPoint.y - vertex.y).toDouble(),
                        (toPoint.x - vertex.x).toDouble()
                    )
                ).toFloat()
                
                startAngle = fromAngle
                var sweep = toAngle - fromAngle
                
                // Normalize sweep angle to draw the smaller arc
                while (sweep > 180) sweep -= 360
                while (sweep < -180) sweep += 360
                sweepAngle = sweep
                ray1Angle = fromAngle
                ray2Angle = toAngle
            }
        }
        
        // Set up arc bounding rectangle centered on vertex
        arcRect.set(
            vertex.x - radius,
            vertex.y - radius,
            vertex.x + radius,
            vertex.y + radius
        )
        
        // Draw filled arc
        canvas.drawArc(arcRect, startAngle, sweepAngle, true, arcFillPaint)
        
        // Draw arc outline
        canvas.drawArc(arcRect, startAngle, sweepAngle, true, arcStrokePaint)
        
        // Draw ray lines from vertex to the arc edges
        val rayLength = radius * 1.2f
        
        // Draw first ray
        val ray1X = vertex.x + rayLength * kotlin.math.cos(Math.toRadians(ray1Angle.toDouble())).toFloat()
        val ray1Y = vertex.y + rayLength * kotlin.math.sin(Math.toRadians(ray1Angle.toDouble())).toFloat()
        canvas.drawLine(vertex.x, vertex.y, ray1X, ray1Y, arcStrokePaint)
        
        // Draw second ray
        val ray2X = vertex.x + rayLength * kotlin.math.cos(Math.toRadians(ray2Angle.toDouble())).toFloat()
        val ray2Y = vertex.y + rayLength * kotlin.math.sin(Math.toRadians(ray2Angle.toDouble())).toFloat()
        canvas.drawLine(vertex.x, vertex.y, ray2X, ray2Y, arcStrokePaint)
    }
    
    /**
     * Draws an angle value with background near the landmark position.
     */
    private fun drawAngleText(canvas: Canvas, position: PointF, angleDisplay: AngleDisplay) {
        // Format angle text
        val angleText = if (angleDisplay.label.isNotEmpty()) {
            "${angleDisplay.label}: ${angleDisplay.angle.toInt()}°"
        } else {
            "${angleDisplay.angle.toInt()}°"
        }
        
        // Measure text
        val textWidth = angleTextPaint.measureText(angleText)
        val textHeight = angleTextPaint.textSize
        
        // Position text offset from the landmark
        // Hip angles go to the left to avoid overlapping with torso
        val offsetX = if (angleDisplay.angleType == AngleType.HIP) {
            -(textWidth + ANGLE_TEXT_OFFSET_X + ANGLE_PADDING * 2)
        } else {
            ANGLE_TEXT_OFFSET_X
        }
        val offsetY = ANGLE_TEXT_OFFSET_Y
        
        val textX = position.x + offsetX
        val textY = position.y + offsetY
        
        // Ensure text stays within view bounds
        val adjustedX = textX.coerceIn(ANGLE_PADDING, width - textWidth - ANGLE_PADDING)
        val adjustedY = textY.coerceIn(textHeight + ANGLE_PADDING, height - ANGLE_PADDING)
        
        // Draw background rectangle
        val padding = ANGLE_PADDING
        angleRect.set(
            adjustedX - padding,
            adjustedY - textHeight - padding / 2,
            adjustedX + textWidth + padding,
            adjustedY + padding
        )
        canvas.drawRoundRect(angleRect, ANGLE_CORNER_RADIUS, ANGLE_CORNER_RADIUS, angleBackgroundPaint)
        
        // Draw text
        canvas.drawText(angleText, adjustedX, adjustedY, angleTextPaint)
    }

    // ==================== Constants ====================
    
    companion object {
        private const val DEFAULT_LANDMARK_RADIUS = 8f
        private const val DEFAULT_SKELETON_STROKE_WIDTH = 4f
        private const val DEFAULT_LANDMARK_COLOR = Color.GREEN
        private const val DEFAULT_SKELETON_COLOR = Color.CYAN
        private const val LOW_CONFIDENCE_COLOR = Color.YELLOW
        private const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.7f
        
        // Angle display constants
        private const val DEFAULT_ANGLE_TEXT_SIZE = 36f
        private const val DEFAULT_ANGLE_TEXT_COLOR = Color.WHITE
        private const val ANGLE_TEXT_OFFSET_X = 20f
        private const val ANGLE_TEXT_OFFSET_Y = -15f
        private const val ANGLE_PADDING = 8f
        private const val ANGLE_CORNER_RADIUS = 6f
        
        // Arc visualization constants
        private const val ARC_RADIUS = 50f
        private const val TORSO_ARC_RADIUS = 35f  // Smaller radius to avoid overlap with hip
        private const val ARC_ALPHA = 100
        private const val ARC_STROKE_ALPHA = 180
        
        // Distinct colors for each angle type
        private const val KNEE_ANGLE_COLOR = 0xFF2196F3.toInt()   // Blue
        private const val HIP_ANGLE_COLOR = 0xFF4CAF50.toInt()    // Green
        private const val ANKLE_ANGLE_COLOR = 0xFFFF9800.toInt()  // Orange
        private const val TORSO_ANGLE_COLOR = 0xFF9C27B0.toInt()  // Purple
        
        /**
         * Skeleton connections for full body visualization.
         * Each pair represents (startLandmarkIndex, endLandmarkIndex).
         */
        val SKELETON_CONNECTIONS: List<Pair<Int, Int>> = listOf(
            // Face
            PoseLandmarkIndex.NOSE to PoseLandmarkIndex.LEFT_EYE,
            PoseLandmarkIndex.NOSE to PoseLandmarkIndex.RIGHT_EYE,
            PoseLandmarkIndex.LEFT_EYE to PoseLandmarkIndex.LEFT_EAR,
            PoseLandmarkIndex.RIGHT_EYE to PoseLandmarkIndex.RIGHT_EAR,
            
            // Torso
            PoseLandmarkIndex.LEFT_SHOULDER to PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.LEFT_SHOULDER to PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.RIGHT_SHOULDER to PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.LEFT_HIP to PoseLandmarkIndex.RIGHT_HIP,
            
            // Left arm
            PoseLandmarkIndex.LEFT_SHOULDER to PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.LEFT_ELBOW to PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.LEFT_WRIST to PoseLandmarkIndex.LEFT_PINKY,
            PoseLandmarkIndex.LEFT_WRIST to PoseLandmarkIndex.LEFT_INDEX,
            PoseLandmarkIndex.LEFT_WRIST to PoseLandmarkIndex.LEFT_THUMB,
            PoseLandmarkIndex.LEFT_PINKY to PoseLandmarkIndex.LEFT_INDEX,
            
            // Right arm
            PoseLandmarkIndex.RIGHT_SHOULDER to PoseLandmarkIndex.RIGHT_ELBOW,
            PoseLandmarkIndex.RIGHT_ELBOW to PoseLandmarkIndex.RIGHT_WRIST,
            PoseLandmarkIndex.RIGHT_WRIST to PoseLandmarkIndex.RIGHT_PINKY,
            PoseLandmarkIndex.RIGHT_WRIST to PoseLandmarkIndex.RIGHT_INDEX,
            PoseLandmarkIndex.RIGHT_WRIST to PoseLandmarkIndex.RIGHT_THUMB,
            PoseLandmarkIndex.RIGHT_PINKY to PoseLandmarkIndex.RIGHT_INDEX,
            
            // Left leg
            PoseLandmarkIndex.LEFT_HIP to PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_KNEE to PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_ANKLE to PoseLandmarkIndex.LEFT_HEEL,
            PoseLandmarkIndex.LEFT_ANKLE to PoseLandmarkIndex.LEFT_FOOT_INDEX,
            PoseLandmarkIndex.LEFT_HEEL to PoseLandmarkIndex.LEFT_FOOT_INDEX,
            
            // Right leg
            PoseLandmarkIndex.RIGHT_HIP to PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE to PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE to PoseLandmarkIndex.RIGHT_HEEL,
            PoseLandmarkIndex.RIGHT_ANKLE to PoseLandmarkIndex.RIGHT_FOOT_INDEX,
            PoseLandmarkIndex.RIGHT_HEEL to PoseLandmarkIndex.RIGHT_FOOT_INDEX
        )
        
        /**
         * Skeleton connections relevant for bike fit analysis.
         * Focuses on side-view landmarks: shoulder, hip, knee, ankle.
         */
        val BIKE_FIT_SKELETON_CONNECTIONS: List<Pair<Int, Int>> = listOf(
            // Left side (typical for side-view analysis)
            PoseLandmarkIndex.LEFT_SHOULDER to PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.LEFT_ELBOW to PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.LEFT_SHOULDER to PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_HIP to PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_KNEE to PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_ANKLE to PoseLandmarkIndex.LEFT_FOOT_INDEX,
            
            // Right side (for opposite view)
            PoseLandmarkIndex.RIGHT_SHOULDER to PoseLandmarkIndex.RIGHT_ELBOW,
            PoseLandmarkIndex.RIGHT_ELBOW to PoseLandmarkIndex.RIGHT_WRIST,
            PoseLandmarkIndex.RIGHT_SHOULDER to PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.RIGHT_HIP to PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE to PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE to PoseLandmarkIndex.RIGHT_FOOT_INDEX
        )
        
        /**
         * Landmark indices relevant for bike fit analysis.
         */
        val BIKE_FIT_LANDMARK_INDICES: List<Int> = listOf(
            // Left side
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_FOOT_INDEX,
            // Right side
            PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.RIGHT_ELBOW,
            PoseLandmarkIndex.RIGHT_WRIST,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.RIGHT_FOOT_INDEX
        )
    }
}
