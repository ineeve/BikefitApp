package pt.ineeve.bikefitapp.calibration

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import pt.ineeve.bikefitapp.R
import pt.ineeve.bikefitapp.camera.CameraManager

/**
 * Activity for calibrating bike reference points.
 * 
 * Guides the user through tapping three reference points on their bike:
 * 1. Saddle top - where the rider sits
 * 2. Bottom bracket - center of the crank axle
 * 3. Handlebar - grip position
 * 
 * These points are used to establish the bike's geometry relative to
 * the rider's body for accurate fit analysis.
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: CalibrationOverlayView
    private lateinit var instructionText: TextView
    private lateinit var confirmButton: Button
    private lateinit var resetButton: Button

    private var calibration: BikeCalibration = BikeCalibration.EMPTY
    private var state: CalibrationState = CalibrationState.WaitingForSaddle

    companion object {
        private const val TAG = "CalibrationActivity"
        
        /** Key for returning calibration result */
        const val RESULT_CALIBRATION = "calibration_result"
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
        setContentView(R.layout.activity_calibration)

        initializeViews()
        setupListeners()
        checkCameraPermissionAndStart()
    }

    private fun initializeViews() {
        previewView = findViewById(R.id.preview_view)
        overlayView = findViewById(R.id.calibration_overlay)
        instructionText = findViewById(R.id.instruction_text)
        confirmButton = findViewById(R.id.confirm_button)
        resetButton = findViewById(R.id.reset_button)

        cameraManager = CameraManager(this)

        updateUI()
    }

    private fun setupListeners() {
        overlayView.onPointTappedListener = { normalizedX, normalizedY ->
            onPointTapped(normalizedX, normalizedY)
        }
        
        overlayView.onPointAdjustedListener = { pointType, normalizedX, normalizedY ->
            onPointAdjusted(pointType, normalizedX, normalizedY)
        }

        confirmButton.setOnClickListener {
            onConfirmClicked()
        }

        resetButton.setOnClickListener {
            onResetClicked()
        }
    }

    private fun checkCameraPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraPreview()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
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

    private fun startCameraPreview() {
        // Start camera without frame analysis for calibration
        cameraManager.startCamera(
            lifecycleOwner = this,
            previewView = previewView,
            frameAnalysisCallback = null,  // No frame processing needed
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
     * Handles a tap on the calibration overlay.
     */
    private fun onPointTapped(normalizedX: Float, normalizedY: Float) {
        val pointType = state.getCurrentPointType() ?: return

        Log.d(TAG, "Point tapped: $pointType at ($normalizedX, $normalizedY)")

        // Create the reference point
        val point = BikeReferencePoint(
            type = pointType,
            x = normalizedX,
            y = normalizedY
        )

        // Update calibration
        calibration = calibration.withPoint(point)

        // Advance to next state
        state = when (pointType) {
            BikeReferencePointType.SADDLE_TOP -> CalibrationState.WaitingForBottomBracket
            BikeReferencePointType.BOTTOM_BRACKET -> CalibrationState.WaitingForHandlebar
            BikeReferencePointType.HANDLEBAR -> CalibrationState.ReadyToConfirm
        }

        updateUI()
    }
    
    /**
     * Handles adjustment of an existing calibration point (drag).
     */
    private fun onPointAdjusted(pointType: BikeReferencePointType, normalizedX: Float, normalizedY: Float) {
        Log.d(TAG, "Point adjusted: $pointType to ($normalizedX, $normalizedY)")
        
        // Create the updated reference point
        val point = BikeReferencePoint(
            type = pointType,
            x = normalizedX,
            y = normalizedY
        )
        
        // Update calibration with the adjusted point
        calibration = calibration.withPoint(point)
        
        // Update the overlay to show new position
        overlayView.setCalibration(calibration)
    }

    /**
     * Handles confirm button click.
     */
    private fun onConfirmClicked() {
        if (!calibration.isComplete) {
            Toast.makeText(this, R.string.calibration_incomplete, Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Calibration confirmed: $calibration")

        // Store calibration in repository for access from analysis flow
        CalibrationRepository.setCalibration(calibration)

        state = CalibrationState.Confirmed(calibration)
        updateUI()

        // Show success message
        Toast.makeText(this, R.string.calibration_success, Toast.LENGTH_SHORT).show()

        // Return result to caller
        // For now, just finish - in future, pass data back via intent
        finish()
    }

    /**
     * Handles reset button click.
     */
    private fun onResetClicked() {
        Log.d(TAG, "Calibration reset")

        calibration = BikeCalibration.EMPTY
        state = CalibrationState.WaitingForSaddle
        overlayView.clear()
        updateUI()
    }

    /**
     * Updates all UI elements based on current state.
     */
    private fun updateUI() {
        // Update instruction text
        instructionText.text = state.getInstructionText()

        // Update overlay
        overlayView.setCalibration(calibration)
        overlayView.setState(state)

        // Update button visibility
        when (state) {
            is CalibrationState.ReadyToConfirm -> {
                confirmButton.visibility = View.VISIBLE
                confirmButton.isEnabled = true
            }
            is CalibrationState.Confirmed -> {
                confirmButton.visibility = View.GONE
            }
            else -> {
                confirmButton.visibility = View.VISIBLE
                confirmButton.isEnabled = false
            }
        }

        // Reset button is always visible unless confirmed
        resetButton.visibility = if (state is CalibrationState.Confirmed) {
            View.GONE
        } else {
            View.VISIBLE
        }
        resetButton.isEnabled = calibration.pointCount > 0
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
    }
}
