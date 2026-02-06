package pt.ineeve.bikefitapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import pt.ineeve.bikefitapp.biomechanics.CriticalPedalPosition
import pt.ineeve.bikefitapp.biomechanics.KeyFrameDataPoint
import pt.ineeve.bikefitapp.pose.PoseFrame

/**
 * Custom view that displays a key frame with pose overlay and angle annotations.
 * 
 * Shows:
 * - The captured video frame as background
 * - Pose landmarks (if available)
 * - Angle measurements labeled with values
 * - Position indicator (TDC/BDC/3 O'Clock)
 */
class KeyFrameDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var frameBitmap: Bitmap? = null
    private var frameData: KeyFrameDataPoint? = null
    private var position: CriticalPedalPosition? = null
    private var angles: Map<String, Float> = emptyMap() // Angle labels to values
    
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = 0xFFFFFFFF.toInt() // White
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        color = 0xFFFFFFFF.toInt() // White
        textAlign = Paint.Align.LEFT
        isFakeBoldText = false
    }
    
    private val backgroundPaint = Paint().apply {
        color = 0x99000000.toInt() // Semi-transparent black
    }
    
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt() // Green
        style = Paint.Style.FILL
    }
    
    private val landmarkLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FF00.toInt() // Green
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    
    private val skeletonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00FFFF.toInt() // Cyan for skeleton
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    
    private val anglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFF00.toInt() // Yellow for angles
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    
    private val angleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFF00.toInt() // Yellow
        textSize = 16f
        isFakeBoldText = true
    }

    init {
        framePaint.style = Paint.Style.FILL
    }

    /**
     * Sets the key frame data to display.
     * 
     * @param frameData The key frame data point with bitmap and position info
     * @param angles Map of angle names to their values (e.g., "Knee" -> 145.5f)
     */
    fun setKeyFrame(frameData: KeyFrameDataPoint?, angles: Map<String, Float> = emptyMap()) {
        this.frameData = frameData
        this.frameBitmap = frameData?.bitmap
        this.position = frameData?.position
        this.angles = angles
        invalidate()
    }

    /**
     * Sets the angles to display on the frame.
     * 
     * @param angles Map of angle names to their values
     */
    fun setAngles(angles: Map<String, Float>) {
        this.angles = angles
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = frameBitmap
        if (bitmap == null) {
            // Draw placeholder
            canvas.drawColor(0xFF333333.toInt())
            textPaint.textSize = 40f
            canvas.drawText("No Frame", width / 2f, height / 2f, textPaint)
            return
        }

        // Draw the video frame scaled to fit
        val srcRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val dstRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        
        // Calculate scale to fit frame while maintaining aspect ratio
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
        
        canvas.drawBitmap(bitmap, null, scaledRect, framePaint)
        
        // Draw pose landmarks if available
        drawPoseLandmarks(canvas, scaledRect, bitmap)

        // Draw position label at top
        val positionLabel = when (position) {
            CriticalPedalPosition.TDC -> "TDC (Top Dead Center)"
            CriticalPedalPosition.BDC -> "BDC (Bottom Dead Center)"
            CriticalPedalPosition.THREE_O_CLOCK -> "3 O'Clock (Horizontal)"
            null -> "Unknown Position"
        }
        
        // Draw semi-transparent background for title
        val titleHeight = 50f
        canvas.drawRect(0f, 0f, width.toFloat(), titleHeight, backgroundPaint)
        
        textPaint.textSize = 28f
        canvas.drawText(positionLabel, width / 2f, 35f, textPaint)

        // Draw angles at bottom with semi-transparent background
        if (angles.isNotEmpty()) {
            val angleBoxHeight = (angles.size * 35 + 20).toFloat()
            canvas.drawRect(
                0f,
                (height - angleBoxHeight),
                width.toFloat(),
                height.toFloat(),
                backgroundPaint
            )

            // Draw each angle
            var yOffset = height - angleBoxHeight + 15f
            labelPaint.textSize = 18f
            
            for ((label, value) in angles) {
                val text = "$label: %.1f°".format(value)
                canvas.drawText(text, 16f, yOffset, labelPaint)
                yOffset += 35f
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val maxHeight = MeasureSpec.getSize(heightMeasureSpec)
        
        android.util.Log.d("KeyFrameDisplayView", "onMeasure: width=$width, maxHeight=$maxHeight, heightMode=$heightMode, bitmap=${frameBitmap?.width}x${frameBitmap?.height}")
        
        // If height is explicitly set (not UNSPECIFIED), use it
        if (heightMode == MeasureSpec.EXACTLY) {
            setMeasuredDimension(width, maxHeight)
            return
        }
        
        // Determine aspect ratio from the bitmap if available
        val bitmap = frameBitmap
        if (bitmap != null) {
            val bitmapAspect = bitmap.width.toFloat() / bitmap.height
            val calculatedHeight = (width / bitmapAspect).toInt()
            val finalHeight = when (heightMode) {
                MeasureSpec.AT_MOST -> calculatedHeight.coerceAtMost(maxHeight)
                else -> calculatedHeight
            }
            android.util.Log.d("KeyFrameDisplayView", "onMeasure: calculated height=$calculatedHeight, final=$finalHeight")
            setMeasuredDimension(width, finalHeight)
        } else {
            // Default 16:9 if no bitmap
            val height = (width * 9 / 16f).toInt()
            android.util.Log.d("KeyFrameDisplayView", "onMeasure: no bitmap, using default 16:9 height=$height")
            setMeasuredDimension(width, height)
        }
    }
    
    /**
     * Draws pose landmarks on the scaled frame.
     */
    private fun drawPoseLandmarks(canvas: Canvas, scaledRect: RectF, bitmap: Bitmap) {
        val poseFrame = frameData?.poseFrame ?: return
        
        if (poseFrame.landmarks.isEmpty()) return
        
        // Calculate scale factors from bitmap to view coordinates
        val scaleX = scaledRect.width() / bitmap.width
        val scaleY = scaledRect.height() / bitmap.height
        
        // Convert all landmarks to view coordinates
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
        
        // Draw skeleton connections (bones)
        drawSkeleton(canvas, viewLandmarks)
        
        // Draw angle overlays (knee, hip, ankle)
        drawAngles(canvas, viewLandmarks, poseFrame)
        
        // Draw landmark points on top
        val landmarkRadius = 6f
        for ((_, coords) in viewLandmarks) {
            canvas.drawCircle(coords.first, coords.second, landmarkRadius, landmarkPaint)
        }
    }
    
    /**
     * Draws skeleton connections between joints.
     */
    private fun drawSkeleton(canvas: Canvas, landmarks: Map<Int, Pair<Float, Float>>) {
        // Define skeleton connections (landmark index pairs)
        val connections = listOf(
            // Left side
            Pair(11, 13),  // LEFT_SHOULDER to LEFT_ELBOW
            Pair(13, 15),  // LEFT_ELBOW to LEFT_WRIST
            Pair(11, 23),  // LEFT_SHOULDER to LEFT_HIP
            Pair(23, 25),  // LEFT_HIP to LEFT_KNEE
            Pair(25, 27),  // LEFT_KNEE to LEFT_ANKLE
            Pair(27, 29),  // LEFT_ANKLE to LEFT_HEEL
            Pair(27, 31),  // LEFT_ANKLE to LEFT_FOOT_INDEX
            // Right side
            Pair(12, 14),  // RIGHT_SHOULDER to RIGHT_ELBOW
            Pair(14, 16),  // RIGHT_ELBOW to RIGHT_WRIST
            Pair(12, 24),  // RIGHT_SHOULDER to RIGHT_HIP
            Pair(24, 26),  // RIGHT_HIP to RIGHT_KNEE
            Pair(26, 28),  // RIGHT_KNEE to RIGHT_ANKLE
            Pair(28, 30),  // RIGHT_ANKLE to RIGHT_HEEL
            Pair(28, 32),  // RIGHT_ANKLE to RIGHT_FOOT_INDEX
            // Torso
            Pair(11, 12),  // LEFT_SHOULDER to RIGHT_SHOULDER
            Pair(23, 24),  // LEFT_HIP to RIGHT_HIP
            Pair(11, 23),  // LEFT_SHOULDER to LEFT_HIP
            Pair(12, 24)   // RIGHT_SHOULDER to RIGHT_HIP
        )
        
        for ((from, to) in connections) {
            val fromCoord = landmarks[from] ?: continue
            val toCoord = landmarks[to] ?: continue
            canvas.drawLine(fromCoord.first, fromCoord.second, toCoord.first, toCoord.second, skeletonPaint)
        }
    }
    
    /**
     * Draws angle overlays at key joints.
     */
    private fun drawAngles(canvas: Canvas, landmarks: Map<Int, Pair<Float, Float>>, poseFrame: PoseFrame) {
        // Draw key angles for bike fit
        
        // Left side angles
        drawJointAngle(canvas, landmarks, 11, 23, 25, "L Hip-Knee", 20f)  // LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE
        drawJointAngle(canvas, landmarks, 23, 25, 27, "L Knee", -20f)     // LEFT_HIP, LEFT_KNEE, LEFT_ANKLE
        drawJointAngle(canvas, landmarks, 25, 27, 31, "L Ankle", -40f)    // LEFT_KNEE, LEFT_ANKLE, LEFT_FOOT_INDEX
        
        // Right side angles
        drawJointAngle(canvas, landmarks, 12, 24, 26, "R Hip-Knee", 20f)  // RIGHT_SHOULDER, RIGHT_HIP, RIGHT_KNEE
        drawJointAngle(canvas, landmarks, 24, 26, 28, "R Knee", -20f)     // RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE
        drawJointAngle(canvas, landmarks, 26, 28, 32, "R Ankle", -40f)    // RIGHT_KNEE, RIGHT_ANKLE, RIGHT_FOOT_INDEX
    }
    
    /**
     * Draws an angle arc at a joint between three landmarks.
     */
    private fun drawJointAngle(
        canvas: Canvas,
        landmarks: Map<Int, Pair<Float, Float>>,
        fromIdx: Int,
        vertexIdx: Int,
        toIdx: Int,
        label: String,
        labelOffset: Float
    ) {
        val fromCoord = landmarks[fromIdx] ?: return
        val vertexCoord = landmarks[vertexIdx] ?: return
        val toCoord = landmarks[toIdx] ?: return
        
        // Draw lines from vertex to both points
        canvas.drawLine(fromCoord.first, fromCoord.second, vertexCoord.first, vertexCoord.second, anglePaint)
        canvas.drawLine(vertexCoord.first, vertexCoord.second, toCoord.first, toCoord.second, anglePaint)
        
        // Draw label near the angle
        val labelX = vertexCoord.first + 15f
        val labelY = vertexCoord.second + labelOffset
        canvas.drawText(label, labelX, labelY, angleTextPaint)
    }
}
