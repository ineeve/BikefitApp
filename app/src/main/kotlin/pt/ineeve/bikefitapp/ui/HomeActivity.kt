package pt.ineeve.bikefitapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import pt.ineeve.bikefitapp.R
import pt.ineeve.bikefitapp.camera.CameraPreviewActivity

class HomeActivity : AppCompatActivity() {

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCameraActivity()
        } else {
            Toast.makeText(this, "Camera permission needed for real-time analysis", Toast.LENGTH_SHORT).show()
        }
    }

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val intent = Intent(this, VideoAnalysisActivity::class.java)
            intent.putExtra(VideoAnalysisActivity.EXTRA_VIDEO_URI, uri.toString())
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<Button>(R.id.realtime_button).setOnClickListener {
            checkCameraPermissionAndStart()
        }

        findViewById<Button>(R.id.gallery_button).setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }
    }

    private fun checkCameraPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraActivity()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(this, "Camera access is required", Toast.LENGTH_SHORT).show()
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraActivity() {
        startActivity(Intent(this, CameraPreviewActivity::class.java))
    }
}
