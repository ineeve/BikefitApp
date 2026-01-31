package pt.ineeve.bikefitapp.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var frameCallback: FrameAnalysisCallback? = null
    private val frameSampler = FrameSampler()

    companion object {
        private const val TAG = "CameraManager"
        
        /** Default camera to use - back camera for bike fit analysis */
        val DEFAULT_CAMERA_SELECTOR: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }

    /**
     * Starts the camera preview bound to the given lifecycle.
     * 
     * @param lifecycleOwner The lifecycle owner to bind the camera to
     * @param previewView The PreviewView to display the camera feed
     * @param cameraSelector Which camera to use (default: back camera)
     * @param frameAnalysisCallback Optional callback to receive frames for analysis
     * @param targetFps Target frames per second for analysis (default: 10 FPS)
     * @param onError Callback for error handling
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraSelector: CameraSelector = DEFAULT_CAMERA_SELECTOR,
        frameAnalysisCallback: FrameAnalysisCallback? = null,
        targetFps: Float = FrameSampler.DEFAULT_TARGET_FPS,
        onError: ((Exception) -> Unit)? = null
    ) {
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
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            Log.d(TAG, "Camera preview and analysis started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /**
     * Processes a single frame from the camera.
     * Applies frame sampling and converts ImageProxy to Bitmap.
     * Invokes the callback on background thread only for sampled frames.
     */
    private fun processFrame(imageProxy: androidx.camera.core.ImageProxy) {
        try {
            val callback = frameCallback
            if (callback != null) {
                val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000 // Convert ns to ms
                
                // Apply frame sampling - skip frames that are too close together
                if (frameSampler.shouldProcessFrame(timestampMs)) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
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
        cameraProvider?.unbindAll()
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
