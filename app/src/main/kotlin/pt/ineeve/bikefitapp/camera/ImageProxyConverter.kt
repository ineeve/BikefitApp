package pt.ineeve.bikefitapp.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Utility object for converting CameraX ImageProxy to Bitmap.
 * 
 * Handles YUV_420_888 format conversion which is the default format
 * from CameraX ImageAnalysis.
 */
object ImageProxyConverter {

    /**
     * Converts an ImageProxy to a Bitmap.
     * 
     * @param imageProxy The ImageProxy from CameraX ImageAnalysis
     * @param rotationDegrees The rotation to apply (from imageProxy.imageInfo.rotationDegrees)
     * @return A Bitmap in ARGB_8888 format, rotated to display correctly
     */
    fun toBitmap(imageProxy: ImageProxy, rotationDegrees: Int = 0): Bitmap {
        val bitmap = when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> yuv420ToBitmap(imageProxy)
            ImageFormat.JPEG -> jpegToBitmap(imageProxy)
            else -> throw IllegalArgumentException("Unsupported image format: ${imageProxy.format}")
        }
        
        return if (rotationDegrees != 0) {
            rotateBitmap(bitmap, rotationDegrees)
        } else {
            bitmap
        }
    }

    /**
     * Converts YUV_420_888 format to Bitmap.
     * This is the default format from CameraX ImageAnalysis.
     */
    private fun yuv420ToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        // Copy VU planes (NV21 format: YYYYVUVUVU)
        val uvPixelStride = imageProxy.planes[1].pixelStride
        if (uvPixelStride == 1) {
            // Packed UV planes
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
        } else {
            // Interleaved UV planes - need to handle pixel stride
            val uvWidth = imageProxy.width / 2
            val uvHeight = imageProxy.height / 2
            var uvIndex = ySize

            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val vIndex = row * imageProxy.planes[2].rowStride + col * uvPixelStride
                    val uIndex = row * imageProxy.planes[1].rowStride + col * uvPixelStride
                    
                    if (vIndex < vSize) {
                        vBuffer.position(vIndex)
                        nv21[uvIndex++] = vBuffer.get()
                    }
                    if (uIndex < uSize) {
                        uBuffer.position(uIndex)
                        nv21[uvIndex++] = uBuffer.get()
                    }
                }
            }
        }

        // Reset buffer positions
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            90,
            outputStream
        )

        val jpegBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    /**
     * Converts JPEG format to Bitmap.
     */
    private fun jpegToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Rotates a bitmap by the specified degrees.
     */
    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }
}
