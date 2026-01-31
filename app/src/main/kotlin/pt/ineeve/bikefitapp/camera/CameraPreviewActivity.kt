package pt.ineeve.bikefitapp.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import pt.ineeve.bikefitapp.R

/**
 * Activity that displays the camera preview for bike fit analysis.
 * 
 * Handles camera permissions, manages the CameraX preview lifecycle,
 * and provides frame analysis capability.
 */
class CameraPreviewActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var previewView: PreviewView
    
    /** Counter for logging frame analysis (debug purposes) */
    private var frameCount = 0L

    companion object {
        private const val TAG = "CameraPreviewActivity"
        /** Log every Nth frame to avoid log spam */
        private const val LOG_FRAME_INTERVAL = 30
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCameraPreview()
        } else {
            Toast.makeText(
                this,
                getString(R.string.camera_permission_denied),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)

        previewView = findViewById(R.id.preview_view)
        cameraManager = CameraManager(this)

        checkCameraPermissionAndStart()
    }

    /**
     * Checks for camera permission and requests it if not granted.
     */
    private fun checkCameraPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraPreview()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show explanation to the user, then request permission
                Toast.makeText(
                    this,
                    getString(R.string.camera_permission_rationale),
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    /**
     * Starts the camera preview after permission is granted.
     */
    private fun startCameraPreview() {
        cameraManager.startCamera(
            lifecycleOwner = this,
            previewView = previewView,
            frameAnalysisCallback = this::onFrameReceived,
            onError = { exception ->
                Toast.makeText(
                    this,
                    getString(R.string.camera_error, exception.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    /**
     * Called for each frame received from the camera.
     * This runs on a background thread - do not update UI directly.
     * 
     * @param bitmap The camera frame as a Bitmap
     * @param timestampMs Frame timestamp in milliseconds
     * @param rotationDegrees Rotation applied to the bitmap
     */
    private fun onFrameReceived(bitmap: Bitmap, timestampMs: Long, rotationDegrees: Int) {
        frameCount++
        
        // Log periodically to verify frames are being received
        if (frameCount % LOG_FRAME_INTERVAL == 0L) {
            Log.d(TAG, "Frame #$frameCount received: ${bitmap.width}x${bitmap.height}, " +
                    "timestamp=$timestampMs ms, rotation=$rotationDegrees°")
        }
        
        // TODO: Pass bitmap to pose estimation in future issues
        // For now, just recycle the bitmap to free memory
        bitmap.recycle()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
    }
}
