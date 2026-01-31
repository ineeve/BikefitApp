package pt.ineeve.bikefitapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import pt.ineeve.bikefitapp.pose.Landmark
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import pt.ineeve.bikefitapp.pose.PoseResult

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

    // ==================== State ====================
    
    private var currentPose: PoseResult? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    
    /** Cached transformed coordinates */
    private val transformedLandmarks = mutableMapOf<Int, PointF>()

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
        imageWidth = width
        imageHeight = height
        isMirrored = isFrontCamera
        transformedLandmarks.clear()
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
     * Clears the current pose overlay.
     */
    fun clear() {
        currentPose = null
        transformedLandmarks.clear()
        invalidate()
    }
    
    /**
     * Returns true if a valid pose is currently being displayed.
     */
    fun hasPose(): Boolean = currentPose?.isValid == true

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
     * Transforms a landmark from normalized coordinates to view coordinates.
     */
    private fun transformCoordinates(landmark: Landmark): PointF {
        var x = landmark.x * width
        val y = landmark.y * height
        
        // Mirror horizontally if front camera
        if (isMirrored) {
            x = width - x
        }
        
        return PointF(x, y)
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

    // ==================== Constants ====================
    
    companion object {
        private const val DEFAULT_LANDMARK_RADIUS = 8f
        private const val DEFAULT_SKELETON_STROKE_WIDTH = 4f
        private const val DEFAULT_LANDMARK_COLOR = Color.GREEN
        private const val DEFAULT_SKELETON_COLOR = Color.CYAN
        private const val LOW_CONFIDENCE_COLOR = Color.YELLOW
        private const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.7f
        
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
