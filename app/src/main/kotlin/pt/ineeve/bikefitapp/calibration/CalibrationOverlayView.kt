package pt.ineeve.bikefitapp.calibration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

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

    /** Listener for tap events */
    var onPointTappedListener: ((Float, Float) -> Unit)? = null

    /** Current calibration data to display */
    private var calibration: BikeCalibration = BikeCalibration.EMPTY

    /** Current calibration state for visual hints */
    private var state: CalibrationState = CalibrationState.WaitingForSaddle

    // Paint objects for drawing
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
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

    companion object {
        /** Radius of the point marker circle */
        private const val POINT_RADIUS = 24f
        
        /** Radius of the outer ring */
        private const val POINT_OUTER_RADIUS = 32f
        
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
     * Updates the current calibration state.
     */
    fun setState(state: CalibrationState) {
        this.state = state
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Normalize coordinates to 0-1 range
            val normalizedX = event.x / width
            val normalizedY = event.y / height
            onPointTappedListener?.invoke(normalizedX, normalizedY)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw connection lines between points
        drawConnectionLines(canvas)
        
        // Draw each marked point
        calibration.saddleTop?.let { point ->
            drawPoint(canvas, point, "Saddle", COLOR_SADDLE)
        }
        
        calibration.bottomBracket?.let { point ->
            drawPoint(canvas, point, "BB", COLOR_BOTTOM_BRACKET)
        }
        
        calibration.handlebar?.let { point ->
            drawPoint(canvas, point, "Handlebar", COLOR_HANDLEBAR)
        }
        
        // Draw hint for next point to tap
        drawNextPointHint(canvas)
    }

    /**
     * Draws a reference point with label.
     */
    private fun drawPoint(canvas: Canvas, point: BikeReferencePoint, label: String, color: Int) {
        val x = point.x * width
        val y = point.y * height
        
        // Draw outer ring
        pointStrokePaint.color = Color.WHITE
        canvas.drawCircle(x, y, POINT_OUTER_RADIUS, pointStrokePaint)
        
        // Draw filled circle
        pointPaint.color = color
        canvas.drawCircle(x, y, POINT_RADIUS, pointPaint)
        
        // Draw label background
        val labelWidth = labelPaint.measureText(label) + 20f
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
        canvas.drawText(label, x, y + LABEL_OFFSET_Y + 10f, labelPaint)
    }

    /**
     * Draws lines connecting the reference points.
     */
    private fun drawConnectionLines(canvas: Canvas) {
        val points = mutableListOf<PointF>()
        
        calibration.saddleTop?.let { 
            points.add(PointF(it.x * width, it.y * height))
        }
        calibration.bottomBracket?.let { 
            points.add(PointF(it.x * width, it.y * height))
        }
        calibration.handlebar?.let { 
            points.add(PointF(it.x * width, it.y * height))
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
}
