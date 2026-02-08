package pt.ineeve.bikefitapp.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import pt.ineeve.bikefitapp.R
import pt.ineeve.bikefitapp.biomechanics.CriticalPedalPosition
import pt.ineeve.bikefitapp.biomechanics.KeyFrameDataPoint
import pt.ineeve.bikefitapp.pose.PoseFrame
import kotlin.math.sqrt

/**
 * Activity that displays a fullscreen image with pose overlays and pinch-to-zoom capability.
 * 
 * This activity provides:
 * - Fullscreen image display with pose landmarks and skeleton
 * - Pinch-to-zoom gesture support
 * - Pan support for zoomed images
 * - Double-tap to reset zoom
 * - Angle overlays matching the analysis view
 * 
 * Note: Data is passed via static cache to avoid Binder transaction size limits.
 */
class FullscreenImageViewerActivity : AppCompatActivity() {

    private lateinit var zoomableImageView: ZoomableImageView
    
    companion object {
        private const val EXTRA_POSITION_LABEL = "extra_position_label"
        
        // Static cache for data (avoids Binder transaction limits)
        private var cachedBitmap: Bitmap? = null
        private var cachedPoseFrame: PoseFrame? = null
        private var cachedAngles: Map<String, Float>? = null
        
        fun createIntent(
            context: Context,
            bitmap: Bitmap,
            poseFrame: PoseFrame?,
            angles: Map<String, Float>,
            positionLabel: String = ""
        ): Intent {
            // Cache the data to avoid Intent size limits
            cachedBitmap = bitmap
            cachedPoseFrame = poseFrame
            cachedAngles = angles
            return Intent(context, FullscreenImageViewerActivity::class.java).apply {
                putExtra(EXTRA_POSITION_LABEL, positionLabel)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_image_viewer)
        
        zoomableImageView = findViewById(R.id.zoomable_image_view)
        
        // Get data from static cache
        val bitmap = cachedBitmap
        val poseFrame = cachedPoseFrame
        val angles = cachedAngles ?: emptyMap()
        val positionLabel = intent.getStringExtra(EXTRA_POSITION_LABEL) ?: ""
        
        if (bitmap != null) {
            zoomableImageView.setImageData(bitmap, poseFrame, angles)
            supportActionBar?.title = "Bike Fit Analysis - $positionLabel"
        }
    }
    
    override fun onDestroy() {
        // Clear cached data when activity is destroyed
        cachedBitmap = null
        cachedPoseFrame = null
        cachedAngles = null
        super.onDestroy()
    }
}

/**
 * Custom view that displays an image with pose overlays and supports pinch-to-zoom.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var frameBitmap: Bitmap? = null
    private var poseFrame: PoseFrame? = null
    private var angles: Map<String, Float> = emptyMap()
    
    // Zoom and pan variables
    private var scale = 1f
    private var minScale = 1f
    private var maxScale = 5f
    private var panX = 0f
    private var panY = 0f
    
    // Touch variables
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastEventAction = -1
    private var scaleFactor = 1f
    private var previousDistance = 0f
    private var isZooming = false
    
    // Paint objects for overlays
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt() // Green
        style = Paint.Style.FILL
    }
    
    private val skeletonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FFFF.toInt() // Cyan
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    
    private val anglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFF00.toInt() // Yellow
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    
    private val angleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFF00.toInt() // Yellow
        textSize = 16f
        isFakeBoldText = true
    }
    
    private val kneeReferenceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF0000.toInt() // Red
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        strokeCap = Paint.Cap.BUTT
    }
    
    fun setImageData(bitmap: Bitmap, poseFrame: PoseFrame?, angles: Map<String, Float>) {
        this.frameBitmap = bitmap
        this.poseFrame = poseFrame
        this.angles = angles
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val bitmap = frameBitmap ?: return
        
        // Calculate scaled rect to fit bitmap in view while maintaining aspect ratio
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height
        val viewAspect = width.toFloat() / height
        
        val scaledRect = if (bitmapAspect > viewAspect) {
            // Bitmap is wider, fit to width
            val scaledHeight = width / bitmapAspect
            val top = (height - scaledHeight) / 2f
            RectF(0f, top, width.toFloat(), top + scaledHeight)
        } else {
            // Bitmap is taller, fit to height
            val scaledWidth = height * bitmapAspect
            val left = (width - scaledWidth) / 2f
            RectF(left, 0f, left + scaledWidth, height.toFloat())
        }
        
        // Save canvas state for zoom/pan transformations
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scale, scale, scaledRect.centerX(), scaledRect.centerY())
        
        // Draw the bitmap
        canvas.drawBitmap(bitmap, null, scaledRect, null)
        
        // Draw pose overlays if available
        if (poseFrame != null && poseFrame!!.landmarks.isNotEmpty()) {
            drawPoseOverlays(canvas, scaledRect, bitmap)
        }
        
        canvas.restore()
    }
    
    private fun drawPoseOverlays(canvas: Canvas, scaledRect: RectF, bitmap: Bitmap) {
        val poseFrame = poseFrame ?: return
        val scaleX = scaledRect.width() / bitmap.width
        val scaleY = scaledRect.height() / bitmap.height
        
        // Convert landmarks to view coordinates
        val viewLandmarks = poseFrame.landmarks.mapIndexed { index, landmark ->
            if (landmark.visibility >= 0.5f) {
                val pixelX = landmark.x * bitmap.width
                val pixelY = landmark.y * bitmap.height
                val viewX = scaledRect.left + (pixelX * scaleX)
                val viewY = scaledRect.top + (pixelY * scaleY)
                Pair(index, Pair(viewX, viewY))
            } else {
                null
            }
        }.filterNotNull().toMap()
        
        // Draw skeleton
        drawSkeleton(canvas, viewLandmarks)
        
        // Draw knee reference line
        drawKneeReferenceLine(canvas, viewLandmarks, scaledRect)
        
        // Draw landmarks
        val landmarkRadius = 6f
        for ((_, coords) in viewLandmarks) {
            canvas.drawCircle(coords.first, coords.second, landmarkRadius, landmarkPaint)
        }
    }
    
    private fun drawSkeleton(canvas: Canvas, landmarks: Map<Int, Pair<Float, Float>>) {
        val connections = listOf(
            // Left side
            11 to 13, 13 to 15, 11 to 23, 23 to 25, 25 to 27, 27 to 29, 27 to 31,
            // Right side
            12 to 14, 14 to 16, 12 to 24, 24 to 26, 26 to 28, 28 to 30, 28 to 32,
            // Torso
            11 to 12, 23 to 24
        )
        
        for ((from, to) in connections) {
            val fromCoord = landmarks[from] ?: continue
            val toCoord = landmarks[to] ?: continue
            canvas.drawLine(fromCoord.first, fromCoord.second, toCoord.first, toCoord.second, skeletonPaint)
        }
    }
    
    private fun drawKneeReferenceLine(
        canvas: Canvas,
        landmarks: Map<Int, Pair<Float, Float>>,
        scaledRect: RectF
    ) {
        val poseFrame = poseFrame ?: return
        val leftKneeLandmark = poseFrame.landmarks.getOrNull(25) ?: return
        val rightKneeLandmark = poseFrame.landmarks.getOrNull(26) ?: return
        
        // Use the most visible knee
        val kneeCoord = if (leftKneeLandmark.visibility >= rightKneeLandmark.visibility) {
            landmarks[25]
        } else {
            landmarks[26]
        } ?: return
        
        // Draw vertical line from top to bottom of frame
        canvas.drawLine(kneeCoord.first, scaledRect.top, kneeCoord.first, scaledRect.bottom, kneeReferenceLinePaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when {
            event.pointerCount == 2 -> handleMultiTouch(event)
            else -> handleSingleTouch(event)
        }
    }
    
    private fun handleMultiTouch(event: MotionEvent): Boolean {
        val x0 = event.getX(0)
        val y0 = event.getY(0)
        val x1 = event.getX(1)
        val y1 = event.getY(1)
        
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                previousDistance = calculateDistance(x0, y0, x1, y1)
                isZooming = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isZooming && previousDistance > 0) {
                    val currentDistance = calculateDistance(x0, y0, x1, y1)
                    scaleFactor = currentDistance / previousDistance
                    
                    val newScale = scale * scaleFactor
                    if (newScale <= maxScale && newScale >= minScale) {
                        scale = newScale
                    }
                    
                    previousDistance = currentDistance
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                isZooming = false
                previousDistance = 0f
                return true
            }
        }
        return false
    }
    
    private fun handleSingleTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                lastEventAction = MotionEvent.ACTION_DOWN
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scale > 1f) {
                    val deltaX = x - lastTouchX
                    val deltaY = y - lastTouchY
                    
                    panX += deltaX
                    panY += deltaY
                    
                    // Constrain pan
                    val maxPanX = width * (scale - 1) / 2
                    val maxPanY = height * (scale - 1) / 2
                    
                    panX = panX.coerceIn(-maxPanX, maxPanX)
                    panY = panY.coerceIn(-maxPanY, maxPanY)
                    
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (lastEventAction == MotionEvent.ACTION_UP &&
                    (System.currentTimeMillis() - lastEventAction) < 300) {
                    resetZoom()
                }
                lastEventAction = MotionEvent.ACTION_UP
                return true
            }
        }
        return false
    }
    
    private fun resetZoom() {
        scale = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }
    
    private fun calculateDistance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x0 - x1
        val dy = y0 - y1
        return sqrt(dx * dx + dy * dy)
    }
}

