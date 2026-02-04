package pt.ineeve.bikefitapp.calibration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import pt.ineeve.bikefitapp.ui.ViewCoordinateMapper

/**
 * Custom view overlay for displaying and collecting bike calibration points.
 * 
 * This view is placed on top of the camera preview and allows the user to:
 * - Tap to mark bike reference points (saddle, bottom bracket, handlebar)
 * - See visual feedback for marked points
 * - See connection lines between points
 * 
 * Usage:
 * ```xml
 * <pt.ineeve.bikefitapp.calibration.CalibrationOverlayView
 *     android:id="@+id/calibration_overlay"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent" />
 * ```
 */
class CalibrationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Listener for tap events (new point) */
    var onPointTappedListener: ((Float, Float) -> Unit)? = null
    
    /** Listener for point adjustment (existing point moved) */
    var onPointAdjustedListener: ((BikeReferencePointType, Float, Float) -> Unit)? = null
    
    /** Listener for when dragging ends */
    var onDragEndedListener: (() -> Unit)? = null

    /** Current calibration data to display */
    private var calibration: BikeCalibration = BikeCalibration.EMPTY

    /** Current calibration state for visual hints */
    private var state: CalibrationState = CalibrationState.WaitingForSaddle
    
    /** Currently selected/dragging point type */
    private var selectedPoint: BikeReferencePointType? = null
    
    /** Whether we're currently dragging a point */
    private var isDragging = false

    // Paint objects for drawing
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.YELLOW
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
    }

    private val mapper = pt.ineeve.bikefitapp.ui.ViewCoordinateMapper()

    companion object {
        /** Radius of the point marker circle */
        private const val POINT_RADIUS = 24f
        
        /** Radius of the outer ring */
        private const val POINT_OUTER_RADIUS = 32f
        
        /** Touch radius for selecting a point (larger than visual for easier touch) */
        private const val TOUCH_RADIUS = 60f
        
        /** Colors for each point type */
        private val COLOR_SADDLE = Color.rgb(255, 87, 34)      // Deep Orange
        private val COLOR_BOTTOM_BRACKET = Color.rgb(76, 175, 80)  // Green
        private val COLOR_HANDLEBAR = Color.rgb(33, 150, 243)  // Blue
        
        /** Label offset from point */
        private const val LABEL_OFFSET_Y = -50f
    }

    /**
     * Updates the calibration data to display.
     */
    fun setCalibration(calibration: BikeCalibration) {
        this.calibration = calibration
        invalidate()
    }

    /**
     * Sets the current calibration state.
     */
    fun setState(state: CalibrationState) {
        this.state = state
        invalidate()
    }

    /** View scale type for mapping coordinates */
    var scaleType: ViewCoordinateMapper.ScaleType 
        get() = mapper.scaleType
        set(value) {
            mapper.scaleType = value
            invalidate()
        }

    /**
     * Sets the image source info for coordinate mapping.
     */
    fun setImageSourceInfo(width: Int, height: Int, isMirrored: Boolean = false) {
        if (mapper.setDimensions(width, height, getWidth(), getHeight(), isMirrored)) {
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mapper.setDimensions(mapper.imageWidth, mapper.imageHeight, w, h, mapper.isMirrored)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val imagePoint = mapper.mapToImage(event.x, event.y)
        val normalizedX = imagePoint.x
        val normalizedY = imagePoint.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check if touching an existing point
                val touchedPoint = findPointAt(event.x, event.y)
                if (touchedPoint != null) {
                    // Start dragging this point
                    selectedPoint = touchedPoint
                    isDragging = true
                    invalidate()
                    return true
                } else if (state.getCurrentPointType() != null) {
                    // No existing point touched, create new point
                    onPointTappedListener?.invoke(normalizedX, normalizedY)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && selectedPoint != null) {
                    // Update the point position while dragging
                    onPointAdjustedListener?.invoke(selectedPoint!!, normalizedX, normalizedY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    // Finish dragging
                    selectedPoint = null
                    isDragging = false
                    onDragEndedListener?.invoke()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
    
    /**
     * Finds which point (if any) is at the given screen coordinates.
     */
    private fun findPointAt(x: Float, y: Float): BikeReferencePointType? {
        calibration.saddleTop?.let { point ->
            val viewPoint = mapper.mapToView(point.x, point.y)
            if (isPointNear(x, y, viewPoint.x, viewPoint.y)) {
                return BikeReferencePointType.SADDLE_TOP
            }
        }
        calibration.bottomBracket?.let { point ->
            val viewPoint = mapper.mapToView(point.x, point.y)
            if (isPointNear(x, y, viewPoint.x, viewPoint.y)) {
                return BikeReferencePointType.BOTTOM_BRACKET
            }
        }
        calibration.handlebar?.let { point ->
            val viewPoint = mapper.mapToView(point.x, point.y)
            if (isPointNear(x, y, viewPoint.x, viewPoint.y)) {
                return BikeReferencePointType.HANDLEBAR
            }
        }
        return null
    }
    
    /**
     * Checks if a touch point is near a calibration point.
     */
    private fun isPointNear(touchX: Float, touchY: Float, pointX: Float, pointY: Float): Boolean {
        val dx = touchX - pointX
        val dy = touchY - pointY
        return (dx * dx + dy * dy) <= TOUCH_RADIUS * TOUCH_RADIUS
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw connection lines between points
        drawConnectionLines(canvas)
        
        // Draw each marked point
        calibration.saddleTop?.let { point ->
            val isSelected = selectedPoint == BikeReferencePointType.SADDLE_TOP
            drawPoint(canvas, point, "Saddle", COLOR_SADDLE, isSelected)
        }
        
        calibration.bottomBracket?.let { point ->
            val isSelected = selectedPoint == BikeReferencePointType.BOTTOM_BRACKET
            drawPoint(canvas, point, "BB", COLOR_BOTTOM_BRACKET, isSelected)
        }
        
        calibration.handlebar?.let { point ->
            val isSelected = selectedPoint == BikeReferencePointType.HANDLEBAR
            drawPoint(canvas, point, "Handlebar", COLOR_HANDLEBAR, isSelected)
        }
        
        // Draw hint for next point to tap (only if not all points set)
        if (!calibration.isComplete) {
            drawNextPointHint(canvas)
        } else {
            // Draw adjustment hint when all points are set
            drawAdjustmentHint(canvas)
        }
    }

    /**
     * Draws a reference point with label.
     */
    private fun drawPoint(canvas: Canvas, point: BikeReferencePoint, label: String, color: Int, isSelected: Boolean = false) {
        val viewPoint = mapper.mapToView(point.x, point.y)
        val x = viewPoint.x
        val y = viewPoint.y
        
        // Draw selection indicator if selected
        if (isSelected) {
            canvas.drawCircle(x, y, POINT_OUTER_RADIUS + 12f, selectedStrokePaint)
        }
        
        // Draw outer ring
        pointStrokePaint.color = Color.WHITE
        canvas.drawCircle(x, y, POINT_OUTER_RADIUS, pointStrokePaint)
        
        // Draw filled circle
        pointPaint.color = color
        canvas.drawCircle(x, y, POINT_RADIUS, pointPaint)
        
        // Draw label background
        val displayLabel = if (isSelected) "⟷ $label" else label
        val labelWidth = labelPaint.measureText(displayLabel) + 20f
        val labelHeight = 40f
        canvas.drawRoundRect(
            x - labelWidth / 2,
            y + LABEL_OFFSET_Y - labelHeight / 2,
            x + labelWidth / 2,
            y + LABEL_OFFSET_Y + labelHeight / 2,
            8f, 8f,
            labelBackgroundPaint
        )
        
        // Draw label text
        canvas.drawText(displayLabel, x, y + LABEL_OFFSET_Y + 10f, labelPaint)
    }
    
    /**
     * Draws a hint to let the user know they can adjust points.
     */
    private fun drawAdjustmentHint(canvas: Canvas) {
        val hintText = "Drag points to adjust • Tap Confirm when ready"
        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 0, 0, 0)
        }
        
        val textWidth = hintPaint.measureText(hintText) + 40f
        val y = height * 0.92f  // Near bottom
        
        canvas.drawRoundRect(
            width / 2f - textWidth / 2f,
            y - 25f,
            width / 2f + textWidth / 2f,
            y + 15f,
            12f, 12f,
            bgPaint
        )
        canvas.drawText(hintText, width / 2f, y, hintPaint)
    }

    /**
     * Draws lines connecting the reference points.
     */
    private fun drawConnectionLines(canvas: Canvas) {
        val points = mutableListOf<PointF>()
        
        calibration.saddleTop?.let { 
            points.add(mapper.mapToView(it.x, it.y))
        }
        calibration.bottomBracket?.let { 
            points.add(mapper.mapToView(it.x, it.y))
        }
        calibration.handlebar?.let { 
            points.add(mapper.mapToView(it.x, it.y))
        }
        
        // Draw lines between consecutive points
        for (i in 0 until points.size - 1) {
            linePaint.color = Color.argb(150, 255, 255, 255)
            canvas.drawLine(
                points[i].x, points[i].y,
                points[i + 1].x, points[i + 1].y,
                linePaint
            )
        }
        
        // If all points present, draw line from handlebar to saddle
        if (points.size == 3) {
            linePaint.color = Color.argb(100, 255, 255, 255)
            canvas.drawLine(
                points[2].x, points[2].y,  // Handlebar
                points[0].x, points[0].y,  // Saddle
                linePaint
            )
        }
    }

    /**
     * Draws a pulsing hint for the next point to tap.
     */
    private fun drawNextPointHint(canvas: Canvas) {
        val pointType = state.getCurrentPointType() ?: return
        
        val color = when (pointType) {
            BikeReferencePointType.SADDLE_TOP -> COLOR_SADDLE
            BikeReferencePointType.BOTTOM_BRACKET -> COLOR_BOTTOM_BRACKET
            BikeReferencePointType.HANDLEBAR -> COLOR_HANDLEBAR
        }
        
        // Draw a more visible pulsing target at expected position
        val (hintX, hintY) = when (pointType) {
            BikeReferencePointType.SADDLE_TOP -> Pair(0.3f, 0.3f)  // Upper left area
            BikeReferencePointType.BOTTOM_BRACKET -> Pair(0.4f, 0.7f)  // Lower center
            BikeReferencePointType.HANDLEBAR -> Pair(0.7f, 0.35f)  // Right side
        }
        
        val centerX = hintX * width
        val centerY = hintY * height
        
        // Draw target rings (outer to inner)
        val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        
        // Outer ring
        targetPaint.alpha = 80
        canvas.drawCircle(centerX, centerY, 80f, targetPaint)
        
        // Middle ring  
        targetPaint.alpha = 120
        canvas.drawCircle(centerX, centerY, 50f, targetPaint)
        
        // Inner filled circle
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 60
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, 30f, fillPaint)
        
        // Draw crosshair
        targetPaint.alpha = 150
        targetPaint.strokeWidth = 2f
        canvas.drawLine(centerX - 90f, centerY, centerX - 35f, centerY, targetPaint)
        canvas.drawLine(centerX + 35f, centerY, centerX + 90f, centerY, targetPaint)
        canvas.drawLine(centerX, centerY - 90f, centerX, centerY - 35f, targetPaint)
        canvas.drawLine(centerX, centerY + 35f, centerX, centerY + 90f, targetPaint)
        
        // Draw arrow pointing to target with label
        drawTargetLabel(canvas, centerX, centerY, pointType, color)
    }
    
    /**
     * Draws a label with arrow pointing to the target area.
     */
    private fun drawTargetLabel(canvas: Canvas, targetX: Float, targetY: Float, 
                                 pointType: BikeReferencePointType, color: Int) {
        val label = when (pointType) {
            BikeReferencePointType.SADDLE_TOP -> "👆 TAP HERE\nSaddle Top"
            BikeReferencePointType.BOTTOM_BRACKET -> "👆 TAP HERE\nBottom Bracket"
            BikeReferencePointType.HANDLEBAR -> "👆 TAP HERE\nHandlebar"
        }
        
        // Position label below the target
        val labelY = targetY + 120f
        
        val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(200, 0, 0, 0)
        }
        
        val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 32f
            this.color = color
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        
        // Draw background
        val lines = label.split("\n")
        val lineHeight = 40f
        val bgHeight = lines.size * lineHeight + 20f
        val bgWidth = 200f
        
        canvas.drawRoundRect(
            targetX - bgWidth / 2,
            labelY - 10f,
            targetX + bgWidth / 2,
            labelY + bgHeight,
            12f, 12f,
            labelBgPaint
        )
        
        // Draw text lines
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, targetX, labelY + 30f + (index * lineHeight), labelTextPaint)
        }
    }

    /**
     * Clears all points and resets the view.
     */
    fun clear() {
        calibration = BikeCalibration.EMPTY
        state = CalibrationState.WaitingForSaddle
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}
    