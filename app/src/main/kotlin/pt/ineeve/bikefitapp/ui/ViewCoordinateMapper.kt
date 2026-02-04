package pt.ineeve.bikefitapp.ui

import android.graphics.PointF

/**
 * Handles coordinate transformation between camera image coordinates (normalized 0-1)
 * and View coordinates (pixels), accounting for scaling and cropping.
 */
class ViewCoordinateMapper {

    enum class ScaleType {
        FILL_CENTER, // Matches CameraX PreviewView default (Zoom to fill)
        FIT_CENTER   // Matches ImageView fitCenter (Letterbox)
    }

    var scaleType: ScaleType = ScaleType.FILL_CENTER
    var imageWidth: Int = 0
    var imageHeight: Int = 0
    var viewWidth: Int = 0
    var viewHeight: Int = 0
    var isMirrored: Boolean = false

    /**
     * Updates dimensions. Returns true if any dimension changed.
     */
    fun setDimensions(imgWidth: Int, imgHeight: Int, vWidth: Int, vHeight: Int, mirrored: Boolean = false): Boolean {
        if (imageWidth == imgWidth && imageHeight == imgHeight && 
            viewWidth == vWidth && viewHeight == vHeight && isMirrored == mirrored) {
            return false
        }
        imageWidth = imgWidth
        imageHeight = imgHeight
        viewWidth = vWidth
        viewHeight = vHeight
        isMirrored = mirrored
        return true
    }

    /**
     * Transforms normalized image coordinates (0-1) to view pixel coordinates.
     */
    fun mapToView(normalizedX: Float, normalizedY: Float): PointF {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            var x = normalizedX * viewWidth
            val y = normalizedY * viewHeight
            if (isMirrored) x = viewWidth - x
            return PointF(x, y)
        }

        val viewAspectRatio = viewWidth.toFloat() / viewHeight
        val imageAspectRatio = imageWidth.toFloat() / imageHeight
        
        val scaleFactor = calculateScaleFactor(viewAspectRatio, imageAspectRatio)

        val scaledWidth = imageWidth * scaleFactor
        val scaledHeight = imageHeight * scaleFactor

        val xOffset = (viewWidth - scaledWidth) / 2
        val yOffset = (viewHeight - scaledHeight) / 2

        var x = (normalizedX * imageWidth * scaleFactor) + xOffset
        val y = (normalizedY * imageHeight * scaleFactor) + yOffset
        
        if (isMirrored) {
            x = viewWidth - x
        }
        
        return PointF(x, y)
    }

    /**
     * Transforms view pixel coordinates to normalized image coordinates (0-1).
     * Used for mapping user taps to camera image space.
     */
    fun mapToImage(viewX: Float, viewY: Float): PointF {
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            var x = viewX / viewWidth
            val y = viewY / viewHeight
            if (isMirrored) x = 1.0f - x
            return PointF(x, y)
        }

        val viewAspectRatio = viewWidth.toFloat() / viewHeight
        val imageAspectRatio = imageWidth.toFloat() / imageHeight
        
        val scaleFactor = calculateScaleFactor(viewAspectRatio, imageAspectRatio)

        val scaledWidth = imageWidth * scaleFactor
        val scaledHeight = imageHeight * scaleFactor

        val xOffset = (viewWidth - scaledWidth) / 2
        val yOffset = (viewHeight - scaledHeight) / 2

        var realViewX = viewX
        if (isMirrored) {
            realViewX = viewWidth - viewX
        }

        val normalizedX = (realViewX - xOffset) / (imageWidth * scaleFactor)
        val normalizedY = (viewY - yOffset) / (imageHeight * scaleFactor)
        
        return PointF(
            normalizedX.coerceIn(0f, 1f),
            normalizedY.coerceIn(0f, 1f)
        )
    }

    private fun calculateScaleFactor(viewAspectRatio: Float, imageAspectRatio: Float): Float {
        return when (scaleType) {
            ScaleType.FILL_CENTER -> {
                if (viewAspectRatio > imageAspectRatio) {
                    viewWidth.toFloat() / imageWidth
                } else {
                    viewHeight.toFloat() / imageHeight
                }
            }
            ScaleType.FIT_CENTER -> {
                if (viewAspectRatio > imageAspectRatio) {
                    viewHeight.toFloat() / imageHeight
                } else {
                    viewWidth.toFloat() / imageWidth
                }
            }
        }
    }
}
