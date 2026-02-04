package pt.ineeve.bikefitapp.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX camera operations including preview setup and lifecycle binding.
 * 
 * This class encapsulates all CameraX operations to provide a clean interface
 * for displaying camera preview and analyzing frames in the app.
 */
class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var frameCallback: FrameAnalysisCallback? = null
    private val frameSampler = FrameSampler()

    /** Listener for image source information (resolution, rotation) */
    var imageInfoListener: ((width: Int, height: Int, rotation: Int) -> Unit)? = null

    companion object {
        private const val TAG = "CameraManager"
        
        /** Default camera to use - back camera for bike fit analysis */
        val DEFAULT_CAMERA_SELECTOR: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        /** Minimum zoom ratio (widest view) */
        const val MIN_ZOOM_RATIO = 1.0f
    }

    /**
     * Updates the target rotation for all use cases.
     * Call this when the device rotation changes.
     * 
     * @param rotation The new rotation (e.g. Surface.ROTATION_0, ROTATION_90, etc.)
     */
    fun updateTargetRotation(rotation: Int) {
        preview?.targetRotation = rotation
        imageAnalysis?.targetRotation = rotation
        Log.d(TAG, "Target rotation updated: $rotation")
    }

    /**
     * Starts the camera preview bound to the given lifecycle.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraSelector: CameraSelector = DEFAULT_CAMERA_SELECTOR,
        frameAnalysisCallback: FrameAnalysisCallback? = null,
        targetFps: Float = FrameSampler.DEFAULT_TARGET_FPS,
        onError: ((Exception) -> Unit)? = null
    ) {
        // Ensure executor is available (recreate if previously shutdown)
        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        
        this.frameCallback = frameAnalysisCallback
        frameSampler.setTargetFps(targetFps)
        frameSampler.reset()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, previewView, cameraSelector)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
                onError?.invoke(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Sets or updates the frame analysis callback.
     * Can be called after camera is started to enable/disable frame analysis.
     * 
     * @param callback The callback to receive frames, or null to disable
     */
    fun setFrameAnalysisCallback(callback: FrameAnalysisCallback?) {
        this.frameCallback = callback
    }

    /**
     * Sets the target FPS for frame analysis.
     * 
     * @param fps Target frames per second (clamped to 1-60 FPS)
     */
    fun setTargetFps(fps: Float) {
        frameSampler.setTargetFps(fps)
    }

    /**
     * Binds the camera use cases (preview and optionally image analysis) to the lifecycle.
     */
    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraSelector: CameraSelector
    ) {
        val provider = cameraProvider ?: run {
            Log.e(TAG, "Camera provider is null")
            return
        }

        // Unbind any existing use cases before rebinding
        provider.unbindAll()

        // Build the preview use case
        preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }

        // Build the image analysis use case
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        try {
            // Bind both preview and image analysis to the camera and lifecycle
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            
            // Set zoom to minimum (widest view) for bike fit analysis
            setZoomToMinimum()
            
            Log.d(TAG, "Camera preview and analysis started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }
    
    /**
     * Sets the camera zoom to the minimum ratio (widest field of view).
     * This is important for bike fit analysis to capture the full bike and rider.
     * Uses a delayed retry to ensure zoom state is available.
     */
    private fun setZoomToMinimum() {
        camera?.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value
            if (zoomState != null) {
                val minZoom = zoomState.minZoomRatio
                cam.cameraControl.setZoomRatio(minZoom)
                Log.d(TAG, "Zoom set to minimum: $minZoom")
            } else {
                // Zoom state not ready yet, retry after a short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    camera?.let { c ->
                        val state = c.cameraInfo.zoomState.value
                        val minZoom = state?.minZoomRatio ?: MIN_ZOOM_RATIO
                        c.cameraControl.setZoomRatio(minZoom)
                        Log.d(TAG, "Zoom set to minimum (delayed): $minZoom")
                    }
                }, 100)
            }
        }
    }
    
    /**
     * Sets the camera zoom ratio.
     * 
     * @param ratio The zoom ratio to set. Will be clamped to the camera's supported range.
     */
    fun setZoomRatio(ratio: Float) {
        camera?.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value
            val minZoom = zoomState?.minZoomRatio ?: MIN_ZOOM_RATIO
            val maxZoom = zoomState?.maxZoomRatio ?: ratio
            val clampedRatio = ratio.coerceIn(minZoom, maxZoom)
            cam.cameraControl.setZoomRatio(clampedRatio)
            Log.d(TAG, "Zoom set to: $clampedRatio (requested: $ratio)")
        }
    }
    
    /**
     * Gets the current zoom ratio.
     * 
     * @return The current zoom ratio, or null if camera is not initialized
     */
    fun getZoomRatio(): Float? {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio
    }

    /**
     * Processes a single frame from the camera.
     * Applies frame sampling and converts ImageProxy to Bitmap.
     * Invokes the callback on background thread only for sampled frames.
     */
    private fun processFrame(imageProxy: androidx.camera.core.ImageProxy) {
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            
            // Notify listener about image info if changed (using rotated dimensions)
            val isRotated = rotationDegrees == 90 || rotationDegrees == 270
            val bitmapWidth = if (isRotated) imageProxy.height else imageProxy.width
            val bitmapHeight = if (isRotated) imageProxy.width else imageProxy.height
            
            imageInfoListener?.let { listener ->
                Handler(Looper.getMainLooper()).post {
                    listener(bitmapWidth, bitmapHeight, rotationDegrees)
                }
            }

            val callback = frameCallback
            if (callback != null) {
                val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000 // Convert ns to ms
                
                // Apply frame sampling - skip frames that are too close together
                if (frameSampler.shouldProcessFrame(timestampMs)) {
                    val bitmap = ImageProxyConverter.toBitmap(imageProxy, rotationDegrees)
                    callback.onFrameAvailable(bitmap, timestampMs, rotationDegrees)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            // Always close the imageProxy to allow the next frame
            imageProxy.close()
        }
    }

    /**
     * Stops the camera and releases resources.
     * Called automatically when the lifecycle is destroyed, but can be called manually.
     */
    fun stopCamera() {
        val useCases = mutableListOf<UseCase>()
        preview?.let { useCases.add(it) }
        imageAnalysis?.let { useCases.add(it) }

        if (useCases.isNotEmpty()) {
            cameraProvider?.unbind(*useCases.toTypedArray())
        }
        Log.d(TAG, "Camera stopped")
    }

    /**
     * Releases all camera resources.
     * Should be called when the camera is no longer needed.
     */
    fun shutdown() {
        stopCamera()
        cameraExecutor.shutdown()
        Log.d(TAG, "Camera manager shut down")
    }
}
