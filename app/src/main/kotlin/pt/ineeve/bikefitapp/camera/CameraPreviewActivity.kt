package pt.ineeve.bikefitapp.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import pt.ineeve.bikefitapp.R

/**
 * Activity that displays the camera preview for bike fit analysis.
 * 
 * Handles camera permissions and manages the CameraX preview lifecycle.
 */
class CameraPreviewActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var previewView: PreviewView

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
            onError = { exception ->
                Toast.makeText(
                    this,
                    getString(R.string.camera_error, exception.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
    }
}
