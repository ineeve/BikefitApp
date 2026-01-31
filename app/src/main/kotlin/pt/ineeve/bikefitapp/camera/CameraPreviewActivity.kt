package pt.ineeve.bikefitapp.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import pt.ineeve.bikefitapp.R
import pt.ineeve.bikefitapp.biomechanics.BodySide
import pt.ineeve.bikefitapp.biomechanics.CycleSummary
import pt.ineeve.bikefitapp.biomechanics.KneeAngleCalculator
import pt.ineeve.bikefitapp.calibration.BikeCalibration
import pt.ineeve.bikefitapp.calibration.CalibrationActivity
import pt.ineeve.bikefitapp.calibration.CalibrationRepository
import pt.ineeve.bikefitapp.fit.FitAnalysisInput
import pt.ineeve.bikefitapp.fit.FitEngine
import pt.ineeve.bikefitapp.fit.FitSummary
import pt.ineeve.bikefitapp.pose.PoseLandmarkerWrapper
import pt.ineeve.bikefitapp.pose.PoseResult
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import pt.ineeve.bikefitapp.ui.AngleDisplay
import pt.ineeve.bikefitapp.ui.AnalysisStatus
import pt.ineeve.bikefitapp.ui.AnalysisStatusView
import pt.ineeve.bikefitapp.ui.BikeOverlayView
import pt.ineeve.bikefitapp.ui.FitSummaryActivity
import pt.ineeve.bikefitapp.ui.PoseOverlayView
import pt.ineeve.bikefitapp.ui.RecordingGuidanceView
import pt.ineeve.bikefitapp.ui.StatusMessage
import com.google.mediapipe.tasks.vision.core.RunningMode
import android.view.View
import pt.ineeve.bikefitapp.biomechanics.CycleAggregator
import pt.ineeve.bikefitapp.biomechanics.PedalCycleDetector
import pt.ineeve.bikefitapp.biomechanics.PedalExtremum
import pt.ineeve.bikefitapp.ui.CycleMetricsOverlayView

/**
 * Activity that displays the camera preview for bike fit analysis.
 * 
 * Handles camera permissions, manages the CameraX preview lifecycle,
 * and provides frame analysis capability.
 */
class CameraPreviewActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var previewView: PreviewView
    private lateinit var poseOverlay: PoseOverlayView
    private lateinit var bikeOverlay: BikeOverlayView
    private lateinit var recordingGuidance: RecordingGuidanceView
    private lateinit var analysisStatus: AnalysisStatusView
    private lateinit var startButton: MaterialButton
    private lateinit var cycleMetricsOverlay: CycleMetricsOverlayView
    private var poseLandmarkerWrapper: PoseLandmarkerWrapper? = null
    private var toneGenerator: ToneGenerator? = null
    
    /** Current bike calibration to display on overlay */
    private var bikeCalibration: BikeCalibration? = null
    
    /** Whether calibration has been completed */
    private var hasCalibration = false

    // Real-time analysis components
    private val pedalDetector = PedalCycleDetector()
    private val leftCycleAggregator = CycleAggregator(BodySide.LEFT)
    private val rightCycleAggregator = CycleAggregator(BodySide.RIGHT)
    
    // Data collection thresholds
    // Use 10 half-cycles (5 full revs) to ensure solid data and ignore mounting
    private val MIN_CYCLES_FOR_ANALYSIS = 10
    private var isAnalysisReadyToastShown = false
    
    /** Counter for logging frame analysis (debug purposes) */
    private var frameCount = 0L
    
    /** Counter for poses detected (debug purposes) */
    private var poseCount = 0L
    
    /** Counter for consecutive frames without valid pose (for status messages) */
    private var consecutiveInvalidFrames = 0
    
    /** Threshold for showing status messages */
    private val INVALID_FRAME_THRESHOLD = 15
    
    /** Whether using front camera (for mirroring overlay) */
    private var isFrontCamera = false

    companion object {
        private const val TAG = "CameraPreviewActivity"
        /** Log every Nth frame to avoid log spam */
        private const val LOG_FRAME_INTERVAL = 30
        
        /** Threshold for showing "low confidence" warning */
        private const val LOW_CONFIDENCE_THRESHOLD = 0.5f
        
        /** Minimum visibility for key landmarks to be considered valid */
        private const val MIN_LANDMARK_VISIBILITY = 0.5f

        /** Pose Estimation Frame Rate */
        private const val POSE_ESTIMATION_FPS = 24f
        
        /** Temporary storage for passing calibration between activities.
         * In a production app, this would be handled via a repository or ViewModel. */
        private var pendingCalibration: BikeCalibration? = null
        
        /**
         * Sets the bike calibration to be displayed when the activity starts.
         * Call this before starting CameraPreviewActivity.
         * 
         * @param calibration The bike calibration to display
         */
        fun setPendingCalibration(calibration: BikeCalibration?) {
            pendingCalibration = calibration
        }
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
    
    /** Launcher for CalibrationActivity result */
    private val calibrationLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Calibration returns via CalibrationRepository
        onCalibrationComplete()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_preview)

        previewView = findViewById(R.id.preview_view)
        poseOverlay = findViewById(R.id.pose_overlay)
        bikeOverlay = findViewById(R.id.bike_overlay)
        recordingGuidance = findViewById(R.id.recording_guidance)
        analysisStatus = findViewById(R.id.analysis_status)
        cycleMetricsOverlay = findViewById(R.id.cycle_metrics_overlay)
        startButton = findViewById(R.id.start_button)
        cameraManager = CameraManager(this)
        
        // Initialize tone generator for audio feedback
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }

        // Setup button click listener
        setupStartButton()
        
        // Setup analysis status action callback
        setupAnalysisStatusCallbacks()
        
        // Load bike calibration from intent if provided
        loadBikeCalibration()
        
        // Initialize pose landmarker with VIDEO mode for sequential frame processing
        initializePoseLandmarker()
        
        // Start recording guidance
        startRecordingGuidance()

        checkCameraPermissionAndStart()
    }
    
    /**
     * Sets up callbacks for analysis status action buttons.
     */
    private fun setupAnalysisStatusCallbacks() {
        analysisStatus.onActionClickListener = { status ->
            when (status) {
                AnalysisStatus.BAD_CALIBRATION -> {
                    // Navigate to calibration screen
                    analysisStatus.hideStatus()
                    navigateToCalibration()
                }
                else -> {
                    analysisStatus.hideStatus()
                }
            }
        }
    }
    
    /**
     * Loads bike calibration from pending calibration if available.
     */
    private fun loadBikeCalibration() {
        bikeCalibration = pendingCalibration
        pendingCalibration = null // Clear after loading
        
        bikeCalibration?.let { calibration ->
            Log.d(TAG, "Bike calibration loaded: ${calibration.pointCount}/3 points, complete=${calibration.isComplete}")
            bikeOverlay.setCalibration(calibration)
        }
    }
    
    /**
     * Updates the bike calibration displayed on the overlay.
     * Can be called to update the calibration at runtime.
     * 
     * @param calibration The new bike calibration to display
     */
    fun updateBikeCalibration(calibration: BikeCalibration?) {
        bikeCalibration = calibration
        bikeOverlay.setCalibration(calibration)
    }
    
    /**
     * Starts the recording guidance overlay with tips for the user.
     */
    private fun startRecordingGuidance() {
        recordingGuidance.onDismissListener = {
            Log.d(TAG, "Recording guidance dismissed by user")
        }
        recordingGuidance.onCompleteListener = {
            Log.d(TAG, "Recording guidance completed all tips")
        }
        recordingGuidance.startGuidance()
    }
    
    /**
     * Initializes the MediaPipe Pose Landmarker.
     */
    private fun initializePoseLandmarker() {
        poseLandmarkerWrapper = PoseLandmarkerWrapper(
            context = this,
            runningMode = RunningMode.VIDEO
        )
        
        if (!poseLandmarkerWrapper!!.isReady()) {
            val error = poseLandmarkerWrapper!!.getInitializationError()
            Log.e(TAG, "Failed to initialize PoseLandmarker", error)
            Toast.makeText(
                this,
                "Pose detection unavailable: ${error?.message}",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Log.d(TAG, "PoseLandmarker initialized successfully")
        }
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
                // Camera will be started in onResume
                Log.d(TAG, "Camera permission already granted")
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
        // Reset frame count so image source info is set again for overlays
        frameCount = 0L
        
        cameraManager.startCamera(
            lifecycleOwner = this,
            previewView = previewView,
            frameAnalysisCallback = this::onFrameReceived,
            targetFps = POSE_ESTIMATION_FPS,
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
        
        // Set image source info on first frame for both overlays
        if (frameCount == 1L) {
            runOnUiThread {
                poseOverlay.setImageSourceInfo(bitmap.width, bitmap.height, isFrontCamera)
                bikeOverlay.setImageSourceInfo(bitmap.width, bitmap.height, isFrontCamera)
            }
        }
        
        // Run pose detection on the frame
        val poseResult = poseLandmarkerWrapper?.detectPoseForVideo(bitmap, timestampMs)
            ?: PoseResult.EMPTY
        
        // Calculate knee angles for display
        val angleDisplays = calculateKneeAngles(poseResult)
        
        // Update the pose overlay on the UI thread
        runOnUiThread {
            poseOverlay.updatePose(poseResult)
            poseOverlay.updateAngles(angleDisplays)

            // Show metrics overlay if calibration is complete
            if (hasCalibration) {
                cycleMetricsOverlay.visibility = View.VISIBLE
                
                // Update instantaneous knee angle (prioritize Right if visible, else Left)
                // In a lateral view from the right side, right knee is visible
                val displayAngle = angleDisplays.firstOrNull { it.label == "R" } 
                    ?: angleDisplays.firstOrNull { it.label == "L" }
                
                if (displayAngle != null) {
                    cycleMetricsOverlay.updateCurrentKneeAngle(displayAngle.angle)
                }
            } else {
                cycleMetricsOverlay.visibility = View.GONE
            }
        }
        
        // Process cycle metrics
        processRealTimeMetrics(poseResult, timestampMs, frameCount)

        // Log periodically to verify frames and pose detection
        if (frameCount % LOG_FRAME_INTERVAL == 0L) {
            Log.d(TAG, "Frame #$frameCount: ${bitmap.width}x${bitmap.height}, " +
                    "timestamp=$timestampMs ms, pose_valid=${poseResult.isValid}")
            
            if (poseResult.isValid) {
                logPoseLandmarks(poseResult)
            }
        }
        
        if (poseResult.isValid) {
            poseCount++
            consecutiveInvalidFrames = 0
            
            // Hide any status message if pose becomes valid
            runOnUiThread {
                if (analysisStatus.isStatusShowing()) {
                    analysisStatus.hideStatus()
                }
            }
            
            // Check for low confidence
            if (poseResult.confidence < LOW_CONFIDENCE_THRESHOLD) {
                runOnUiThread {
                    analysisStatus.showLowConfidence()
                }
            }
        } else {
            consecutiveInvalidFrames++
            
            // Show status message after threshold of invalid frames
            if (consecutiveInvalidFrames == INVALID_FRAME_THRESHOLD) {
                runOnUiThread {
                    checkAndShowAnalysisStatus(poseResult)
                }
            }
        }
        
        // Recycle the bitmap to free memory
        bitmap.recycle()
    }
    
    /**
     * Processes pose data to detect pedal cycles and update metrics.
     */
    private fun processRealTimeMetrics(poseResult: PoseResult, timestampMs: Long, frameNumber: Long) {
        if (!hasCalibration || !poseResult.isValid) return

        // Process both sides
        processSideMetrics(poseResult, BodySide.RIGHT, timestampMs, frameNumber)
        processSideMetrics(poseResult, BodySide.LEFT, timestampMs, frameNumber)
    }

    private fun processSideMetrics(
        poseResult: PoseResult, 
        side: BodySide, 
        timestampMs: Long, 
        frameNumber: Long
    ) {
        val kneeIndex = if (side == BodySide.LEFT) PoseLandmarkIndex.LEFT_KNEE else PoseLandmarkIndex.RIGHT_KNEE
        val ankleIndex = if (side == BodySide.LEFT) PoseLandmarkIndex.LEFT_ANKLE else PoseLandmarkIndex.RIGHT_ANKLE
        
        // Check visibility
        val knee = poseResult.getLandmark(kneeIndex)
        val ankle = poseResult.getLandmark(ankleIndex)
        
        if (knee == null || ankle == null || 
            knee.visibility < MIN_LANDMARK_VISIBILITY || 
            ankle.visibility < MIN_LANDMARK_VISIBILITY) {
            return
        }

        // Calculate knee angle
        val kneeResult = KneeAngleCalculator.calculateKneeAngle(poseResult, side)
        val kneeAngle = if (kneeResult.isValid) kneeResult.angle else null

        // Feed aggregator
        val aggregator = if (side == BodySide.LEFT) leftCycleAggregator else rightCycleAggregator
        aggregator.addMeasurement(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            kneeAngle = kneeAngle
        )

        // Detect cycles via ankle position
        // We use processAnklePosition directly since we already extracted ankle data
        val events = pedalDetector.processAnklePosition(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            ankleY = ankle.y,
            visibility = ankle.visibility,
            side = side
        )

        // Handle events
        for (event in events) {
            if (event.type == PedalExtremum.BDC) {
                // Determine knee angle at BDC (Max Extension)
                // Use current frame angle if available, or estimated
                val angleAtBdc = kneeAngle ?: 0f // In real app, might want to look back in buffer
                
                // End cycle and get metrics
                val cycleMetrics = aggregator.endCycleAtBdc(
                    frameNumber = event.frameNumber,
                    timestampMs = event.timestampMs,
                    kneeAngle = angleAtBdc
                )
                
                if (cycleMetrics != null) {
                    // Update UI with latest complete cycle metrics
                    // We only update if this side seems to be the active/calibrated one
                    // or just update both (last writer wins, but usually one side is dominant in view)
                    runOnUiThread {
                        val cyclesLeft = leftCycleAggregator.getCycleCount()
                        val cyclesRight = rightCycleAggregator.getCycleCount()
                        val totalCycles = cyclesLeft + cyclesRight
                        
                        cycleMetricsOverlay.updateCycleMetrics(
                            maxExtension = cycleMetrics.kneeAngleAtBdc ?: cycleMetrics.kneeAngle.max,
                            minFlexion = cycleMetrics.kneeAngleAtTdc ?: cycleMetrics.kneeAngle.min
                        )
                        
                        // Check if we have enough cycles for analysis
                        if (totalCycles >= MIN_CYCLES_FOR_ANALYSIS && !isAnalysisReadyToastShown) {
                            isAnalysisReadyToastShown = true
                            
                            // Visual feedback
                            startButton.setIconResource(R.drawable.ic_check_circle)
                            startButton.text = getString(R.string.analysis_ready)
                            startButton.setBackgroundColor(ContextCompat.getColor(this@CameraPreviewActivity, R.color.analysis_ready_green))
                            
                            // Audio feedback
                            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT)

                            Toast.makeText(
                                this@CameraPreviewActivity,
                                getString(R.string.data_collection_complete_msg),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else if (event.type == PedalExtremum.TDC) {
                // Record TDC for the aggregator
                aggregator.recordTdc(kneeAngle)
            }
        }
    }
    
    /**
     * Logs key pose landmarks for debugging.
     */
    private fun logPoseLandmarks(result: PoseResult) {
        val leftHip = result.getLandmark(PoseLandmarkIndex.LEFT_HIP)
        val leftKnee = result.getLandmark(PoseLandmarkIndex.LEFT_KNEE)
        val leftAnkle = result.getLandmark(PoseLandmarkIndex.LEFT_ANKLE)
        val leftShoulder = result.getLandmark(PoseLandmarkIndex.LEFT_SHOULDER)
        
        Log.d(TAG, "Pose landmarks - " +
                "Hip: (${leftHip?.x?.format()}, ${leftHip?.y?.format()}) vis=${leftHip?.visibility?.format()}, " +
                "Knee: (${leftKnee?.x?.format()}, ${leftKnee?.y?.format()}) vis=${leftKnee?.visibility?.format()}, " +
                "Ankle: (${leftAnkle?.x?.format()}, ${leftAnkle?.y?.format()}) vis=${leftAnkle?.visibility?.format()}")
    }
    
    /**
     * Calculates knee angles from the pose result for display on the overlay.
     * 
     * Returns angle displays for both left and right knees if visible.
     * The angles are positioned at the knee landmark locations.
     * 
     * @param poseResult The pose detection result
     * @return List of valid angle displays
     */
    private fun calculateKneeAngles(poseResult: PoseResult): List<AngleDisplay> {
        if (!poseResult.isValid) return emptyList()
        
        val angles = mutableListOf<AngleDisplay>()
        
        // Calculate left knee angle
        val leftKneeResult = KneeAngleCalculator.calculateKneeAngle(poseResult, BodySide.LEFT)
        if (leftKneeResult.isValid) {
            angles.add(
                AngleDisplay(
                    angle = leftKneeResult.angle,
                    landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
                    isValid = true,
                    label = "L"
                )
            )
        }
        
        // Calculate right knee angle
        val rightKneeResult = KneeAngleCalculator.calculateKneeAngle(poseResult, BodySide.RIGHT)
        if (rightKneeResult.isValid) {
            angles.add(
                AngleDisplay(
                    angle = rightKneeResult.angle,
                    landmarkIndex = PoseLandmarkIndex.RIGHT_KNEE,
                    isValid = true,
                    label = "R"
                )
            )
        }
        
        return angles
    }
    
    /**
     * Formats a float to 2 decimal places, or "null" if null.
     */
    private fun Float?.format(): String = this?.let { "%.2f".format(it) } ?: "null"

    /**
     * Analyzes the pose result and shows appropriate status message.
     * 
     * Checks various failure conditions in order of priority:
     * 1. No calibration - prompts user to recalibrate
     * 2. No person detected - no landmarks at all
     * 3. Missing landmarks - some key landmarks not visible
     * 4. Low confidence - landmarks visible but confidence too low
     */
    private fun checkAndShowAnalysisStatus(poseResult: PoseResult) {
        // Check calibration first
        if (!CalibrationRepository.hasValidCalibration()) {
            analysisStatus.showStatus(StatusMessage.badCalibration())
            return
        }
        
        // Check if we have any landmarks at all
        val landmarks = poseResult.landmarks
        if (landmarks.isEmpty()) {
            analysisStatus.showStatus(StatusMessage.noPersonDetected())
            return
        }
        
        // Check for missing key landmarks (hips, knees, ankles for bike fit)
        val keyLandmarks = listOf(
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE
        )
        
        val visibleKeyLandmarks = keyLandmarks.count { index ->
            landmarks.getOrNull(index)?.let { it.visibility >= MIN_LANDMARK_VISIBILITY } == true
        }
        
        if (visibleKeyLandmarks < keyLandmarks.size / 2) {
            analysisStatus.showStatus(StatusMessage.missingLandmarks())
            return
        }
        
        // Show low confidence warning
        analysisStatus.showStatus(StatusMessage.lowConfidence())
    }
    
    // ==================== Navigation ====================
    
    /**
     * Sets up the start/analyze button.
     */
    private fun setupStartButton() {
        startButton.setOnClickListener {
            if (hasCalibration) {
                navigateToFitSummary()
            } else {
                navigateToCalibration()
            }
        }
        updateStartButton()
    }
    
    /**
     * Updates the start button text based on calibration state.
     */
    private fun updateStartButton() {
        if (hasCalibration) {
            startButton.text = getString(R.string.analyze_fit)
            startButton.setIconResource(R.drawable.ic_camera)
        } else {
            startButton.text = getString(R.string.start_analysis)
            startButton.setIconResource(R.drawable.ic_camera)
        }
    }
    
    /**
     * Navigates to the calibration activity.
     */
    private fun navigateToCalibration() {
        val intent = Intent(this, CalibrationActivity::class.java)
        calibrationLauncher.launch(intent)
    }
    
    /**
     * Called when calibration is complete.
     */
    private fun onCalibrationComplete() {
        // Load calibration from repository
        val calibration = CalibrationRepository.getCalibration()
        if (calibration != null && calibration.isComplete) {
            hasCalibration = true
            bikeCalibration = calibration
            bikeOverlay.setCalibration(calibration)
            updateStartButton()
            
            // Hide any calibration warning that was showing
            analysisStatus.hideStatus()
            
            // Show guidance for next step
            Toast.makeText(
                this,
                getString(R.string.calibration_complete_start_pedaling),
                Toast.LENGTH_LONG
            ).show()
            
            Log.d(TAG, "Calibration loaded: ${calibration.pointCount} points")
        }
    }
    
    /**
     * Navigates to the fit summary activity with analysis results.
     */
    private fun navigateToFitSummary() {
        val calibration = bikeCalibration
        if (calibration == null || !calibration.isComplete) {
            Toast.makeText(this, R.string.calibration_required, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get summary from the side with the most data
        val leftSummary = leftCycleAggregator.getSummary()
        val rightSummary = rightCycleAggregator.getSummary()
        
        val cycleSummary = if (leftSummary.cycleCount >= rightSummary.cycleCount) {
            leftSummary
        } else {
            rightSummary
        }
        
        // Ensure we have enough data
        if (!cycleSummary.isValid) {
             Toast.makeText(this, "Not enough data collected yet. Keep pedaling!", Toast.LENGTH_SHORT).show()
             return
        }

        val input = FitAnalysisInput(
            cycleSummary = cycleSummary,
            bikeCalibration = calibration
        )
        
        val engine = FitEngine()
        val result = engine.analyze(input)
        val summary = FitSummary.fromAnalysisResult(result)
        
        FitSummaryActivity.start(this, summary)
    }

    override fun onPause() {
        super.onPause()
        // Stop the camera when leaving this activity
        // This ensures CalibrationActivity can use the camera
        cameraManager.stopCamera()
    }

    override fun onResume() {
        super.onResume()
        // Restart camera preview when returning from another activity
        // (e.g., CalibrationActivity which also uses the camera)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        toneGenerator = null
        poseLandmarkerWrapper?.close()
        poseLandmarkerWrapper = null
        cameraManager.shutdown()
    }
}
