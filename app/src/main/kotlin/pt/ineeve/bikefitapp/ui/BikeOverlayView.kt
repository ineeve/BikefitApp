package pt.ineeve.bikefitapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import pt.ineeve.bikefitapp.calibration.BikeCalibration
import pt.ineeve.bikefitapp.calibration.BikeReferencePoint
import pt.ineeve.bikefitapp.calibration.BikeReferencePointType

/**
 * Custom View that renders bike reference points on top of camera preview.
 * 
 * This is a read-only overlay that displays the calibrated bike reference points
 * (saddle, bottom bracket, handlebar) during analysis. It is visually distinct
 * from the pose skeleton overlay:
 * - Uses different colors (orange, green, blue)
 * - Uses square/diamond markers instead of circles
 * - Shows dashed connection lines
 * - Displays labels for each point
 * 
 * Usage:
 * ```xml
 * <pt.ineeve.bikefitapp.ui.BikeOverlayView
 *     android:id="@+id/bike_overlay"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent" />
 * ```
 * 
 * ```kotlin
 * val overlay = findViewById<BikeOverlayView>(R.id.bike_overlay)
 * overlay.setCalibration(calibration)
 * ```
 */
class BikeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ==================== Configuration ====================
    
    /** Size of the reference point markers */
    var markerSize: Float = DEFAULT_MARKER_SIZE
        set(value) {
            field = value
            invalidate()
        }
    
    /** Stroke width for marker outlines and connection lines */
    var strokeWidth: Float = DEFAULT_STROKE_WIDTH
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }
    
    /** Whether to show labels for each point */
    var showLabels: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    
    /** Whether to show connection lines between points */
    var showConnections: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    
    /** Alpha value for the overlay (0-255) */
    var overlayAlpha: Int = DEFAULT_ALPHA
        set(value) {
            field = value.coerceIn(0, 255)
            updatePaints()
            invalidate()
        }

    // ==================== Paint Objects ====================
    
    private val saddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_SADDLE
        style = Paint.Style.FILL
    }
    
    private val bottomBracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_BOTTOM_BRACKET
        style = Paint.Style.FILL
    }
    
    private val handlebarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_HANDLEBAR
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = DEFAULT_STROKE_WIDTH
    }
    
    private val connectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }
    
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = DEFAULT_LABEL_SIZE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
    }

    // ==================== State ====================
    
    private var calibration: BikeCalibration? = null
    private val diamondPath = Path()
    private val labelRect = RectF()
    
    // Image source dimensions (for coordinate transformation)
    private val mapper = ViewCoordinateMapper()

    // ==================== Public API ====================
    
    /**
     * Sets the image source information for proper coordinate transformation.
     * Call this when the camera preview dimensions are known.
     * 
     * @param width The image/camera width
     * @param height The image/camera height
     * @param isMirrored Whether the image is mirrored (front camera)
     */
    fun setImageSourceInfo(width: Int, height: Int, isMirrored: Boolean = false) {
        if (mapper.setDimensions(width, height, getWidth(), getHeight(), isMirrored)) {
            invalidate()
        }
    }

    /** View scale type for mapping coordinates */
    var scaleType: ViewCoordinateMapper.ScaleType 
        get() = mapper.scaleType
        set(value) {
            mapper.scaleType = value
            invalidate()
        }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mapper.setDimensions(mapper.imageWidth, mapper.imageHeight, w, h, mapper.isMirrored)
    }
    
    /**
     * Sets the bike calibration data to display.
     * 
     * @param calibration The calibration data, or null to clear
     */
    fun setCalibration(calibration: BikeCalibration?) {
        this.calibration = calibration
        invalidate()
    }
    
    /**
     * Clears the overlay.
     */
    fun clear() {
        calibration = null
        invalidate()
    }
    
    /**
     * Returns true if calibration data is set and complete.
     */
    fun hasCompleteCalibration(): Boolean {
        return calibration?.isComplete == true
    }

    // ==================== Drawing ====================
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val cal = calibration ?: return
        if (!cal.isComplete && cal.pointCount == 0) return
        
        // Draw connection lines first (behind markers)
        if (showConnections) {
            drawConnections(canvas, cal)
        }
        
        // Draw each reference point
        cal.saddleTop?.let { point ->
            drawMarker(canvas, point, saddlePaint, "Saddle")
        }
        
        cal.bottomBracket?.let { point ->
            drawMarker(canvas, point, bottomBracketPaint, "BB")
        }
        
        cal.handlebar?.let { point ->
            drawMarker(canvas, point, handlebarPaint, "Bar")
        }
    }
    
    /**
     * Draws a diamond-shaped marker at the reference point.
     */
    private fun drawMarker(
        canvas: Canvas,
        point: BikeReferencePoint,
        fillPaint: Paint,
        label: String
    ) {
        val pointF = mapper.mapToView(point.x, point.y)
        val x = pointF.x
        val y = pointF.y
        
        // Draw diamond shape
        diamondPath.reset()
        diamondPath.moveTo(x, y - markerSize)  // Top
        diamondPath.lineTo(x + markerSize, y)  // Right
        diamondPath.lineTo(x, y + markerSize)  // Bottom
        diamondPath.lineTo(x - markerSize, y)  // Left
        diamondPath.close()
        
        // Fill
        canvas.drawPath(diamondPath, fillPaint)
        
        // Stroke
        canvas.drawPath(diamondPath, strokePaint)
        
        // Label
        if (showLabels) {
            drawLabel(canvas, x, y - markerSize - LABEL_OFFSET, label)
        }
    }
    
    /**
     * Draws a label with background.
     */
    private fun drawLabel(canvas: Canvas, x: Float, y: Float, text: String) {
        val textWidth = labelPaint.measureText(text)
        val textHeight = labelPaint.textSize
        
        val padding = 8f
        labelRect.set(
            x - textWidth / 2 - padding,
            y - textHeight - padding / 2,
            x + textWidth / 2 + padding,
            y + padding / 2
        )
        
        // Draw background
        canvas.drawRoundRect(labelRect, 6f, 6f, labelBackgroundPaint)
        
        // Draw text
        canvas.drawText(text, x, y, labelPaint)
    }
    
    /**
     * Draws dashed connection lines between points.
     */
    private fun drawConnections(canvas: Canvas, cal: BikeCalibration) {
        val points = mutableListOf<PointF>()
        
        cal.saddleTop?.let { 
            points.add(mapper.mapToView(it.x, it.y))
        }
        cal.bottomBracket?.let { 
            points.add(mapper.mapToView(it.x, it.y))
        }
        cal.handlebar?.let { 
            points.add(mapper.mapToView(it.x, it.y))
        }
        
        // Draw lines between consecutive points
        for (i in 0 until points.size - 1) {
            canvas.drawLine(
                points[i].x, points[i].y,
                points[i + 1].x, points[i + 1].y,
                connectionPaint
            )
        }
        
        // Optionally connect last to first to form triangle
        if (points.size == 3) {
            canvas.drawLine(
                points[2].x, points[2].y,
                points[0].x, points[0].y,
                connectionPaint
            )
        }
    }
    
    /**
     * Transforms normalized coordinates to view coordinates.
     * Handles scaling, centering, and optional mirroring.
     * 
     * @param normalizedX Normalized x coordinate (0.0 to 1.0)
     * @param normalizedY Normalized y coordinate (0.0 to 1.0)
     * @return Pair of (viewX, viewY) pixel coordinates
     */
    private fun transformCoordinates(normalizedX: Float, normalizedY: Float): Pair<Float, Float> {
        val point = mapper.mapToView(normalizedX, normalizedY)
        return Pair(point.x, point.y)
    }
    
    /**
     * Updates paint alpha values.
     */
    private fun updatePaints() {
        saddlePaint.alpha = overlayAlpha
        bottomBracketPaint.alpha = overlayAlpha
        handlebarPaint.alpha = overlayAlpha
        strokePaint.strokeWidth = strokeWidth
        connectionPaint.alpha = (overlayAlpha * 0.6f).toInt()
    }

    // ==================== Constants ====================
    
    companion object {
        private const val DEFAULT_MARKER_SIZE = 16f
        private const val DEFAULT_STROKE_WIDTH = 3f
        private const val DEFAULT_LABEL_SIZE = 28f
        private const val DEFAULT_ALPHA = 230
        private const val LABEL_OFFSET = 20f
        
        // Colors matching CalibrationOverlayView but slightly different shades
        // to indicate this is a read-only view
        val COLOR_SADDLE = Color.rgb(255, 87, 34)       // Deep Orange
        val COLOR_BOTTOM_BRACKET = Color.rgb(76, 175, 80)   // Green
        val COLOR_SPINDLE = Color.rgb(233, 30, 99)      // Pink/Magenta
        val COLOR_HANDLEBAR = Color.rgb(33, 150, 243)   // Blue
        
        /**
         * Returns the color for a given reference point type.
         */
        fun getColorForType(type: BikeReferencePointType): Int {
            return when (type) {
                BikeReferencePointType.SADDLE_TOP -> COLOR_SADDLE
                BikeReferencePointType.BOTTOM_BRACKET -> COLOR_BOTTOM_BRACKET
                BikeReferencePointType.SPINDLE -> COLOR_SPINDLE
                BikeReferencePointType.HANDLEBAR -> COLOR_HANDLEBAR
            }
        }
    }
}
