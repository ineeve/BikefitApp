package pt.ineeve.bikefitapp.calibration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Displays a magnified view of the camera preview for precise calibration point positioning.
 * 
 * Shows a zoomed-in section of the image centered around the point being dragged,
 * with a crosshair to indicate the exact point location.
 * 
 * Usage:
 * ```xml
 * <pt.ineeve.bikefitapp.calibration.MagnifiedPreviewView
 *     android:id="@+id/magnified_preview"
 *     android:layout_width="match_parent"
 *     android:layout_height="200dp" />
 * ```
 */
class MagnifiedPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Current bitmap to magnify */
    private var currentBitmap: Bitmap? = null
    
    /** Point to center magnification around (in normalized coordinates 0-1) */
    private var magnificationCenterX = 0.5f
    private var magnificationCenterY = 0.5f
    
    /** Magnification level */
    private var zoomLevel = 3f
    
    /** Whether the magnified view is visible */
    private var isVisible = false

    // Paint for drawing
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.YELLOW
    }

    private val centerPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.RED
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = android.graphics.Color.WHITE
    }

    companion object {
        /** Default magnification zoom level */
        private const val DEFAULT_ZOOM = 3f
        
        /** Size of the crosshair lines */
        private const val CROSSHAIR_SIZE = 30f
        
        /** Radius of the center point circle */
        private const val CENTER_POINT_RADIUS = 6f
    }

    /**
     * Sets the camera preview bitmap to magnify.
     */
    fun setBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
        invalidate()
    }

    /**
     * Updates the magnification center point.
     * 
     * @param normalizedX X coordinate in normalized space (0-1)
     * @param normalizedY Y coordinate in normalized space (0-1)
     */
    fun setMagnificationPoint(normalizedX: Float, normalizedY: Float) {
        magnificationCenterX = normalizedX.coerceIn(0f, 1f)
        magnificationCenterY = normalizedY.coerceIn(0f, 1f)
        invalidate()
    }

    /**
     * Sets the zoom level for magnification.
     * 
     * @param zoom Zoom factor (e.g., 2f for 2x magnification)
     */
    fun setZoomLevel(zoom: Float) {
        zoomLevel = zoom.coerceAtLeast(1f)
        invalidate()
    }

    /**
     * Shows the magnified preview.
     */
    fun show() {
        isVisible = true
        visibility = VISIBLE
    }

    /**
     * Hides the magnified preview.
     */
    fun hide() {
        isVisible = false
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isVisible || currentBitmap == null) {
            return
        }

        val bitmap = currentBitmap ?: return

        // Calculate the source region (portion of bitmap to magnify)
        val sourceWidth = bitmap.width / zoomLevel
        val sourceHeight = bitmap.height / zoomLevel

        val sourceLeft = max(0, (magnificationCenterX * bitmap.width - sourceWidth / 2).toInt())
        val sourceTop = max(0, (magnificationCenterY * bitmap.height - sourceHeight / 2).toInt())
        val sourceRight = min(bitmap.width, (sourceLeft + sourceWidth.toInt()).toInt())
        val sourceBottom = min(bitmap.height, (sourceTop + sourceHeight.toInt()).toInt())

        // Source rectangle for bitmap sampling
        val srcRect = android.graphics.Rect(sourceLeft, sourceTop, sourceRight, sourceBottom)
        
        // Destination rectangle (fill entire view)
        val dstRect = android.graphics.Rect(0, 0, width, height)

        // Draw the magnified portion of the bitmap
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)

        // Draw border
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

        // Draw crosshair at center
        val centerX = width / 2f
        val centerY = height / 2f

        // Vertical line
        canvas.drawLine(centerX, centerY - CROSSHAIR_SIZE, centerX, centerY + CROSSHAIR_SIZE, crosshairPaint)
        
        // Horizontal line
        canvas.drawLine(centerX - CROSSHAIR_SIZE, centerY, centerX + CROSSHAIR_SIZE, centerY, crosshairPaint)
        
        // Center dot
        canvas.drawCircle(centerX, centerY, CENTER_POINT_RADIUS, centerPointPaint)
    }
}
