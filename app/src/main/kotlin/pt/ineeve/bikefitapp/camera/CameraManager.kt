package pt.ineeve.bikefitapp.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
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
 * for displaying camera preview in the app.
 */
class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

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
     * @param onError Callback for error handling
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraSelector: CameraSelector = DEFAULT_CAMERA_SELECTOR,
        onError: ((Exception) -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindPreview(lifecycleOwner, previewView, cameraSelector)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
                onError?.invoke(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Binds the camera preview to the lifecycle and view.
     */
    private fun bindPreview(
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

        try {
            // Bind the preview to the camera and lifecycle
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            Log.d(TAG, "Camera preview started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera preview", e)
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
