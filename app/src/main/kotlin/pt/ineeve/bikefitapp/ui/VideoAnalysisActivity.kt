package pt.ineeve.bikefitapp.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ineeve.bikefitapp.R
import pt.ineeve.bikefitapp.calibration.*
import pt.ineeve.bikefitapp.biomechanics.*
import pt.ineeve.bikefitapp.fit.FitAnalysisInput
import pt.ineeve.bikefitapp.fit.FitBias
import pt.ineeve.bikefitapp.fit.FitEngine
import pt.ineeve.bikefitapp.fit.FitEngineConfig
import pt.ineeve.bikefitapp.fit.FitSummary
import pt.ineeve.bikefitapp.fit.RidingContext
import pt.ineeve.bikefitapp.pose.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.mediapipe.tasks.vision.core.RunningMode
import pt.ineeve.bikefitapp.ui.AngleDisplay
import pt.ineeve.bikefitapp.ui.AngleType

class VideoAnalysisActivity : AppCompatActivity() {

    private lateinit var videoFrameView: ImageView
    private lateinit var calibrationOverlay: CalibrationOverlayView
    private lateinit var poseOverlay: PoseOverlayView
    private lateinit var cycleMetricsOverlay: CycleMetricsOverlayView
    private lateinit var magnifiedPreviewView: MagnifiedPreviewView
    private lateinit var actionButton: MaterialButton
    private lateinit var progressContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    // Context and Bias selection
    private lateinit var contextBiasCard: MaterialCardView
    private lateinit var contextDropdown: AutoCompleteTextView
    private lateinit var biasDropdown: AutoCompleteTextView
    private var selectedContext: RidingContext = RidingContext.DEFAULT
    private var selectedBias: FitBias = FitBias.DEFAULT

    private var videoUri: Uri? = null
    private var poseLandmarkerWrapper: PoseLandmarkerWrapper? = null

    // Calibration
    private var currentCalibration = BikeCalibration.EMPTY
    private var calibrationState: CalibrationState = CalibrationState.WaitingForSaddle
    private var isDragging = false
    private var currentVideoFrameBitmap: Bitmap? = null

    // Analysis
    private val pedalDetector = PedalCycleDetector()
    private val leftCycleAggregator = CycleAggregator(BodySide.LEFT)
    private val rightCycleAggregator = CycleAggregator(BodySide.RIGHT)
    private val landmarkSmoother = pt.ineeve.bikefitapp.pose.OneEuroLandmarkSmoother()
    
    // Continuous crank angle tracking
    private val crankAngleTracker = CrankAngleTracker
    
    // KOPS crank scale cache (computed once from first 30 frames at 3 o'clock)
    private var crankScaleCacheLeft: KneeOverPedalOffset.CrankScaleCache = KneeOverPedalOffset.CrankScaleCache.INVALID
    private var crankScaleCacheRight: KneeOverPedalOffset.CrankScaleCache = KneeOverPedalOffset.CrankScaleCache.INVALID
    
    // Frames collected at 3 o'clock for crank scale computation
    private val threeOClockFramesLeft = mutableListOf<PoseFrame>()
    private val threeOClockFramesRight = mutableListOf<PoseFrame>()
    
    // Key frame capture for 3 critical positions
    private val leftKeyFrameSet = mutableMapOf<CriticalPedalPosition, Triple<Long, Bitmap?, PoseFrame?>>()
    private val rightKeyFrameSet = mutableMapOf<CriticalPedalPosition, Triple<Long, Bitmap?, PoseFrame?>>()
    
    // Track captured frames to prevent duplicates
    private val capturedFrameNumbers = mutableSetOf<String>()
    
    // Track best frames by confidence (frameNum, confidence) to choose highest confidence
    private var leftTdcBest: Pair<Long, Float>? = null
    private var rightTdcBest: Pair<Long, Float>? = null
    private var leftBdcBest: Pair<Long, Float>? = null
    private var rightBdcBest: Pair<Long, Float>? = null
    private var leftThreeOClockBest: Pair<Long, Float>? = null
    private var rightThreeOClockBest: Pair<Long, Float>? = null
    
    // Track latest crank angle for overlay display
    private var lastCrankAngle: Float? = null
    private var lastCrankAngleLeft: Float? = null
    private var lastCrankAngleRight: Float? = null
    private var lastInstantaneousRpmLeft: Float? = null
    private var lastInstantaneousRpmRight: Float? = null
    
    // Video info
    private var videoDurationMs = 0L
    private var videoActualFps = 0f

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        private const val TAG = "VideoAnalysisActivity"
        private const val MAX_CYCLES_TO_COLLECT = 10
        private const val TARGET_SAMPLING_FPS = 60f
        private const val TARGET_INTERVAL_MS = 1000f / TARGET_SAMPLING_FPS // ~16.67ms
        private const val TARGET_INTERVAL_MICROS = (TARGET_INTERVAL_MS * 1000).toLong() // ~16667 microseconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_analysis)
        
        // Initialize views
        videoFrameView = findViewById(R.id.video_frame_view)
        calibrationOverlay = findViewById(R.id.calibration_overlay)
        calibrationOverlay.scaleType = ViewCoordinateMapper.ScaleType.FIT_CENTER
        poseOverlay = findViewById(R.id.pose_overlay)
        poseOverlay.scaleType = ViewCoordinateMapper.ScaleType.FIT_CENTER
        cycleMetricsOverlay = findViewById(R.id.cycle_metrics_overlay)
        magnifiedPreviewView = findViewById(R.id.magnified_preview)
        
        actionButton = findViewById(R.id.action_button)
        progressContainer = findViewById(R.id.progress_container)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        
        // Context and Bias selection
        contextBiasCard = findViewById(R.id.context_bias_card)
        contextDropdown = findViewById(R.id.context_dropdown)
        biasDropdown = findViewById(R.id.bias_dropdown)

        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (uriString == null) {
            finish()
            return
        }
        videoUri = Uri.parse(uriString)

        setupCalibrationUI()
        setupContextBiasUI()
        loadFirstFrame()
    }

    private fun setupContextBiasUI() {
        // Setup riding context dropdown
        val contexts = RidingContext.values().map { it.displayName }
        val contextAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, contexts)
        contextDropdown.setAdapter(contextAdapter)
        contextDropdown.setText(RidingContext.DEFAULT.displayName, false)
        
        contextDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedContext = RidingContext.values()[position]
        }

        // Setup fit bias dropdown
        val biases = FitBias.values().map { it.displayName }
        val biasAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, biases)
        biasDropdown.setAdapter(biasAdapter)
        biasDropdown.setText(FitBias.DEFAULT.displayName, false)
        
        biasDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedBias = FitBias.values()[position]
        }
    }

    private fun setupCalibrationUI() {
        // Init overlay state
        calibrationOverlay.setCalibration(currentCalibration)
        calibrationOverlay.setState(calibrationState)

        calibrationOverlay.onPointTappedListener = { x, y ->
            handleCalibrationTap(x, y)
        }
        
        calibrationOverlay.onPointAdjustedListener = { type, x, y ->
            // Show magnified view when dragging starts
            if (!isDragging) {
                isDragging = true
                magnifiedPreviewView.show()
            }
            
            // Update magnified view position to follow the point
            magnifiedPreviewView.setMagnificationPoint(x, y)
            
            handleCalibrationAdjustment(type, x, y)
        }
        
        calibrationOverlay.onDragEndedListener = {
            // Hide magnified view when dragging ends
            if (isDragging) {
                isDragging = false
                magnifiedPreviewView.hide()
            }
        }

        actionButton.setOnClickListener {
            if (calibrationState is CalibrationState.ReadyToConfirm || calibrationState is CalibrationState.Confirmed) {
                startAnalysis()
            }
        }
    }

    private fun loadFirstFrame() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this@VideoAnalysisActivity, videoUri)
                
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                videoDurationMs = durationStr?.toLongOrNull() ?: 0L

                // Get video FPS
                val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                videoActualFps = fpsStr?.toFloatOrNull() ?: 0f
                
                // If FPS metadata not available, try calculating from frame count and duration
                if (videoActualFps == 0f && videoDurationMs > 0) {
                    val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    val frameCount = frameCountStr?.toIntOrNull()
                    if (frameCount != null && frameCount > 0) {
                        videoActualFps = (frameCount * 1000f) / videoDurationMs
                    }
                }
                
                Log.d(TAG, "Video FPS: $videoActualFps, Target sampling FPS: $TARGET_SAMPLING_FPS")

                // Try to get first frame
                val frame = retriever.getFrameAtTime(0)
                
                if (frame != null) {
                    withContext(Dispatchers.Main) {
                        videoFrameView.setImageBitmap(frame)
                        currentVideoFrameBitmap = frame
                        magnifiedPreviewView.setBitmap(frame)
                        calibrationOverlay.setImageSourceInfo(frame.width, frame.height)
                        poseOverlay.setImageSourceInfo(frame.width, frame.height)
                    }
                }
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading video", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoAnalysisActivity, "Failed to load video", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun handleCalibrationTap(imageNormX: Float, imageNormY: Float) {
        val type = calibrationState.getCurrentPointType() ?: return

        if (type == BikeReferencePointType.SPINDLE) {
            // Spindle is special - but now store both X and Y
            val point = BikeReferencePoint(type, imageNormX, imageNormY)
            currentCalibration = currentCalibration.withPoint(point)
        } else {
            val point = BikeReferencePoint(type, imageNormX, imageNormY)
            currentCalibration = currentCalibration.withPoint(point)
        }
        
        // Validate when all points are collected
        if (currentCalibration.isComplete) {
            val validationError = currentCalibration.validate()
            if (validationError != null) {
                Toast.makeText(this, "⚠️ $validationError", Toast.LENGTH_LONG).show()
                // Don't proceed - let user adjust the points
                updateOverlay()
                return
            }
        }
        
        // Update state
        proceedCalibrationState()
        
        // Update UI
        updateOverlay()
    }

    private fun handleCalibrationAdjustment(type: BikeReferencePointType, imageNormX: Float, imageNormY: Float) {
        if (type == BikeReferencePointType.SPINDLE) {
            // Spindle adjustment
            val point = BikeReferencePoint(type, imageNormX, imageNormY)
            currentCalibration = currentCalibration.withPoint(point)
        } else {
            val point = BikeReferencePoint(type, imageNormX, imageNormY)
            currentCalibration = currentCalibration.withPoint(point)
        }
        
        // Update UI
        updateOverlay()
    }
    
    // Update Calibration Overlay with current points
    private fun updateOverlay() {
        calibrationOverlay.setCalibration(currentCalibration)
        calibrationOverlay.setState(calibrationState)
    }
    
    /**
     * Shows a dialog to collect crank length from the user.
     * Typical crank lengths: 165, 170, 172.5, 175, 177.5, 180 mm
     */
    private fun showCrankLengthDialog() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "e.g., 170, 172.5, 175"
            setText("172.5") // Default common crank length
            selectAll()
        }
        
        val container = android.widget.FrameLayout(this).apply {
            val params = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            val marginPx = (16 * resources.displayMetrics.density).toInt() // 16dp
            val marginTopPx = (8 * resources.displayMetrics.density).toInt() // 8dp
            params.setMargins(marginPx, marginTopPx, marginPx, 0)
            addView(input, params)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Crank Length")
            .setMessage("Enter your crank length in millimeters.\\n\\nCommon sizes:\\n• Road bikes: 170-175mm\\n• MTB: 170-175mm\\n• TT/Tri: 165-172.5mm\\n\\nCheck your crank arm for markings.")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val crankLengthText = input.text.toString()
                try {
                    val crankLength = crankLengthText.toFloat().toInt()
                    if (crankLength in 160..185) {
                        currentCalibration = currentCalibration.copy(crankLengthMm = crankLength)
                        proceedCalibrationState()
                        updateOverlay()
                    } else {
                        Toast.makeText(this, "Crank length must be between 160-185mm", Toast.LENGTH_LONG).show()
                        showCrankLengthDialog() // Show again
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_LONG).show()
                    showCrankLengthDialog() // Show again
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                // Use default 172.5mm if skipped
                currentCalibration = currentCalibration.copy(crankLengthMm = 172)
                Toast.makeText(this, "Using default crank length: 172.5mm", Toast.LENGTH_SHORT).show()
                proceedCalibrationState()
                updateOverlay()
            }
            .setCancelable(false)
            .show()
        
        // Request focus and show keyboard
        input.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun proceedCalibrationState() {
        calibrationState = when (calibrationState) {
            is CalibrationState.WaitingForSaddle -> CalibrationState.WaitingForBottomBracket
            is CalibrationState.WaitingForBottomBracket -> CalibrationState.WaitingForSpindle
            is CalibrationState.WaitingForSpindle -> CalibrationState.WaitingForHandlebar
            is CalibrationState.WaitingForHandlebar -> {
                // Show crank length input dialog after handlebar is marked
                showCrankLengthDialog()
                CalibrationState.WaitingForCrankLength
            }
            is CalibrationState.WaitingForCrankLength -> {
                actionButton.text = "Start Analysis"
                actionButton.visibility = View.VISIBLE
                contextBiasCard.visibility = View.VISIBLE
                CalibrationState.ReadyToConfirm
            }
            is CalibrationState.ReadyToConfirm -> CalibrationState.Confirmed(currentCalibration)
            is CalibrationState.Confirmed -> CalibrationState.Confirmed(currentCalibration)
        }
    }
    private fun startAnalysis() {
        calibrationOverlay.visibility = View.GONE
        poseOverlay.visibility = View.VISIBLE
        cycleMetricsOverlay.visibility = View.VISIBLE
        actionButton.visibility = View.GONE
        contextBiasCard.visibility = View.GONE
        progressContainer.visibility = View.VISIBLE
        
        // Reset tracking state for new analysis
        crankAngleTracker.reset()
        
        // Initialize Pose Detector
        poseLandmarkerWrapper = PoseLandmarkerWrapper(
            context = this,
            runningMode = RunningMode.VIDEO
        )
        
        lifecycleScope.launch(Dispatchers.Default) {
             analyzeFrames()
        }
    }

    private suspend fun analyzeFrames() {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this@VideoAnalysisActivity, videoUri)
        } catch(e: Exception) {
            withContext(Dispatchers.Main) { 
                Toast.makeText(this@VideoAnalysisActivity, "Error reading video", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }
        
        // Check if video FPS is less than target and show warning
        if (videoActualFps > 0 && videoActualFps < TARGET_SAMPLING_FPS) {
            Log.w(TAG, "Video FPS ($videoActualFps) is less than target sampling rate ($TARGET_SAMPLING_FPS fps)")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@VideoAnalysisActivity,
                    "Warning: Video recorded at ${"%.1f".format(videoActualFps)} fps (target: ${TARGET_SAMPLING_FPS.toInt()} fps)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        // Estimate frames based on 60fps sampling
        val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
        val frameCount = frameCountStr?.toIntOrNull() ?: ((videoDurationMs / TARGET_INTERVAL_MS).toInt())
        
        var analyzedFrames = 0
        
        // Iterate. For short videos, process every frame (API 28+). 
        // If API < 28, use time based.
        val framesToProcess = if (frameCount > 0) frameCount else 1
        
        for (i in 0 until framesToProcess) {
            // Check cancellation
            if (!coroutineContext.isActive) break
            
            // Stop early if we have collected enough cycles
            val totalCyclesCollected = leftCycleAggregator.getCycleCount() + rightCycleAggregator.getCycleCount()
            if (totalCyclesCollected >= MAX_CYCLES_TO_COLLECT) {
                Log.d(TAG, "Stopping analysis: collected $totalCyclesCollected cycles")
                break
            }

            val frame = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                     retriever.getFrameAtIndex(i)
                } else {
                     retriever.getFrameAtTime(i * TARGET_INTERVAL_MICROS, MediaMetadataRetriever.OPTION_CLOSEST)
                }
            } catch (e: Exception) { null }

            if (frame != null) {
                withContext(Dispatchers.Main) {
                    videoFrameView.setImageBitmap(frame)
                }
                // Use 60fps timestamp regardless of video's actual FPS for consistent analysis timing.
                // If video < 60fps, frames may be duplicated, but this ensures uniform temporal resolution.
                val timestampMs = (i * TARGET_INTERVAL_MS).toLong()
                processFrame(frame, timestampMs, i.toLong())
            }

            analyzedFrames++
            if (analyzedFrames % 10 == 0) {
                withContext(Dispatchers.Main) {
                    progressBar.progress = (analyzedFrames * 100 / framesToProcess)
                    statusText.text = "Processing frame $analyzedFrames / $framesToProcess"
                }
            }
        }
        
        retriever.release()
        
        withContext(Dispatchers.Main) {
            finishAnalysis()
        }
    }
    private suspend fun processFrame(bitmap: Bitmap, timestampMs: Long, frameNumber: Long) {
         val rawPoseResult = poseLandmarkerWrapper?.detectPoseForVideo(bitmap, timestampMs) ?: PoseResult.EMPTY
         
         // Apply One Euro filtering for temporal smoothing
         val poseResult = landmarkSmoother.smooth(rawPoseResult)
         
         if (poseResult.isValid) {
             // Calculate all angles for display
             val angleDisplays = calculateAllAngles(poseResult)
             
             withContext(Dispatchers.Main) {
                 poseOverlay.setImageSourceInfo(bitmap.width, bitmap.height)
                 poseOverlay.updatePose(poseResult)
                 poseOverlay.updateAngles(angleDisplays)
                 
                 // Update crank angle display if available
                 poseOverlay.setCrankAngle(lastCrankAngle)
                 
                 // Update cycle count
                 val cyclesLeft = leftCycleAggregator.getCycleCount()
                 val cyclesRight = rightCycleAggregator.getCycleCount()
                 val totalCycles = cyclesLeft + cyclesRight
                 cycleMetricsOverlay.updateCycleCount(totalCycles)
                 
                 // Update metrics from the calculated angle displays (uses dominant side)
                 angleDisplays.forEach { display ->
                     when (display.angleType) {
                         AngleType.KNEE -> cycleMetricsOverlay.updateCurrentKneeAngle(display.angle)
                         AngleType.HIP -> cycleMetricsOverlay.updateCurrentHipAngle(display.angle)
                         AngleType.TORSO -> cycleMetricsOverlay.updateCurrentTorsoAngle(display.angle)
                         AngleType.ANKLE -> { /* Not shown in live metrics panel */ }
                     }
                 }
                 
                 // Update live crank angle and cadence from tracker
                 lastCrankAngle?.let { 
                     cycleMetricsOverlay.updateCurrentCrankAngle(it)
                     Log.d(TAG, "UI update: crank angle = $it°")
                 }
                 // Display instantaneous RPM from whichever side has latest data
                 val rpm = lastInstantaneousRpmLeft ?: lastInstantaneousRpmRight ?: 0f
                 if (rpm > 0) {
                     cycleMetricsOverlay.updateCurrentCadence(rpm)
                     Log.d(TAG, "UI update: cadence = $rpm RPM")
                 }
             }
             
             // Process both sides for crank angle tracking (ensures continuous data)
             // But only process cycle metrics for the dominant side
             val dominantSide = detectDominantSide(poseResult)
             processSideMetrics(poseResult, BodySide.LEFT, timestampMs, frameNumber, bitmap)
             processSideMetrics(poseResult, BodySide.RIGHT, timestampMs, frameNumber, bitmap)
         }
    }

    /**
     * Calculates all angles from the pose result for display on the overlay.
     * 
     * Only displays angles for the dominant side (the side more visible to the camera).
     * Each angle includes arc data for geometric visualization.
     * 
     * @param poseResult The pose detection result
     * @return List of valid angle displays
     */
    private fun calculateAllAngles(poseResult: PoseResult): List<AngleDisplay> {
        if (!poseResult.isValid) return emptyList()
        
        // Determine which side is more visible to the camera
        val dominantSide = detectDominantSide(poseResult)
        
        val angles = mutableListOf<AngleDisplay>()
        
        // Get landmark indices based on dominant side
        val kneeIndex = if (dominantSide == BodySide.LEFT) PoseLandmarkIndex.LEFT_KNEE else PoseLandmarkIndex.RIGHT_KNEE
        val hipIndex = if (dominantSide == BodySide.LEFT) PoseLandmarkIndex.LEFT_HIP else PoseLandmarkIndex.RIGHT_HIP
        val ankleIndex = if (dominantSide == BodySide.LEFT) PoseLandmarkIndex.LEFT_ANKLE else PoseLandmarkIndex.RIGHT_ANKLE
        val shoulderIndex = if (dominantSide == BodySide.LEFT) PoseLandmarkIndex.LEFT_SHOULDER else PoseLandmarkIndex.RIGHT_SHOULDER
        val footIndex = if (dominantSide == BodySide.LEFT) PoseLandmarkIndex.LEFT_FOOT_INDEX else PoseLandmarkIndex.RIGHT_FOOT_INDEX
        val sideLabel = if (dominantSide == BodySide.LEFT) "L" else "R"
        
        // Calculate knee angle (Hip -> Knee -> Ankle)
        val kneeResult = KneeAngleCalculator.calculateKneeAngle(poseResult, dominantSide)
        if (kneeResult.isValid) {
            angles.add(AngleDisplay(
                angle = kneeResult.angle,
                landmarkIndex = kneeIndex,
                fromLandmarkIndex = hipIndex,
                toLandmarkIndex = ankleIndex,
                angleType = AngleType.KNEE,
                isValid = true,
                label = "$sideLabel Knee"
            ))
        }
        
        // Calculate hip angle (Shoulder -> Hip -> Knee)
        val hipResult = HipAngleCalculator.calculateHipAngle(poseResult, dominantSide)
        if (hipResult.isValid) {
            angles.add(AngleDisplay(
                angle = hipResult.angle,
                landmarkIndex = hipIndex,
                fromLandmarkIndex = shoulderIndex,
                toLandmarkIndex = kneeIndex,
                angleType = AngleType.HIP,
                isValid = true,
                label = "$sideLabel Hip"
            ))
        }
        
        // Calculate ankle angle (at intersection of knee-ankle line and heel-foot line)
        val ankleResult = AnkleAngleCalculator.calculateAnkleAngle(poseResult, dominantSide)
        if (ankleResult.isValid) {
            angles.add(AngleDisplay(
                angle = ankleResult.angle,
                landmarkIndex = ankleIndex,
                fromLandmarkIndex = kneeIndex,
                toLandmarkIndex = footIndex,
                angleType = AngleType.ANKLE,
                isValid = true,
                label = "$sideLabel Ankle",
                customVertexX = ankleResult.intersectionX,
                customVertexY = ankleResult.intersectionY
            ))
        }
        
        // Calculate torso angle (Shoulder -> Hip vs horizontal)
        val torsoResult = TorsoAngleCalculator.calculateTorsoAngle(poseResult, dominantSide)
        if (torsoResult.isValid) {
            angles.add(AngleDisplay(
                angle = torsoResult.angle,
                landmarkIndex = hipIndex,
                fromLandmarkIndex = shoulderIndex,
                toLandmarkIndex = -1, // Horizontal reference
                angleType = AngleType.TORSO,
                isValid = true,
                label = "$sideLabel Torso"
            ))
        }
        
        return angles
    }
    
    /**
     * Determines which side of the body to analyze based on bike orientation.
     * 
     * Uses the bike calibration to determine orientation:
     * - If bike faces left (handlebars left of saddle), analyze LEFT body side
     * - If bike faces right (handlebars right of saddle), analyze RIGHT body side
     * 
     * Falls back to visibility-based detection if calibration is unavailable.
     * 
     * @param poseResult The pose detection result
     * @return The body side visible to camera
     */
    private fun detectDominantSide(poseResult: PoseResult): BodySide {
        // Try to use bike orientation if calibration is complete
        currentCalibration.getCameraSide()?.let { return it }
        
        // Fallback: visibility-based detection
        val leftIndices = listOf(
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_ANKLE
        )
        
        val rightIndices = listOf(
            PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.RIGHT_ANKLE
        )
        
        val leftVisibility = leftIndices.mapNotNull { index ->
            poseResult.getLandmark(index)?.visibility
        }.average().takeIf { !it.isNaN() } ?: 0.0
        
        val rightVisibility = rightIndices.mapNotNull { index ->
            poseResult.getLandmark(index)?.visibility
        }.average().takeIf { !it.isNaN() } ?: 0.0
        
        return if (leftVisibility >= rightVisibility) BodySide.LEFT else BodySide.RIGHT
    }

    private fun processSideMetrics(poseResult: PoseResult, side: BodySide, timestampMs: Long, frameNumber: Long, currentFrameBitmap: Bitmap) {
        val kneeIndex = if (side == BodySide.LEFT) PoseLandmarkIndex.LEFT_KNEE else PoseLandmarkIndex.RIGHT_KNEE
        val ankleIndex = if (side == BodySide.LEFT) PoseLandmarkIndex.LEFT_ANKLE else PoseLandmarkIndex.RIGHT_ANKLE
        
        val knee = poseResult.getLandmark(kneeIndex)
        val ankle = poseResult.getLandmark(ankleIndex)

        if (knee == null || ankle == null) return

        val kneeResult = KneeAngleCalculator.calculateKneeAngle(poseResult, side)
        val kneeAngle = if (kneeResult.isValid) kneeResult.angle else null
        
        val hipResult = HipAngleCalculator.calculateHipAngle(poseResult, side)
        val hipAngle = if (hipResult.isValid) hipResult.angle else null
        
        val torsoResult = TorsoAngleCalculator.calculateTorsoAngle(poseResult, side)
        val torsoAngle = if (torsoResult.isValid) torsoResult.angle else null
        
        // Calculate ankle angle
        val ankleResult = AnkleAngleCalculator.calculateAnkleAngle(poseResult, side)
        val ankleAngle = if (ankleResult.isValid) ankleResult.angle else null
        
        // Create pose frame for KOPS computation
        val poseFrame = PoseFrame(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            landmarks = poseResult.landmarks,
            confidence = poseResult.confidence
        )
        
        // Collect 3 o'clock frames for crank scale computation (8 frames provides ~3-4 rotation cycles for averaging)
        // Use CrankAngleTracker to get measurements near 3 o'clock (90°)
        val threeOClockFramesList = if (side == BodySide.LEFT) threeOClockFramesLeft else threeOClockFramesRight
        val crankScaleCache = if (side == BodySide.LEFT) crankScaleCacheLeft else crankScaleCacheRight
        
        // Track continuous crank angle for all frames using foot landmark
        // Use foot index instead of ankle for better pedal position tracking
        val footIndex = if (side == BodySide.LEFT) PoseLandmarkIndex.LEFT_FOOT_INDEX else PoseLandmarkIndex.RIGHT_FOOT_INDEX
        val foot = poseResult.getLandmark(footIndex) ?: ankle // Fallback to ankle if foot not available
        
        val rawCrankAngle = crankAngleTracker.computeRawCrankAngle(
            footX = foot.x,
            footY = foot.y,
            bbX = currentCalibration.bottomBracket?.x ?: 0f,
            bbY = currentCalibration.bottomBracket?.y ?: 0f
        )
        if (rawCrankAngle >= 0 && currentCalibration.bottomBracket != null) {
            // Get calibrated camera side from bike calibration
            val calibratedSide = currentCalibration.getCameraSide()
            
            val trackingResult = crankAngleTracker.trackAngle(
                rawAngle = rawCrankAngle,
                frameNumber = frameNumber,
                timestampMs = timestampMs,
                side = side,
                calibratedSide = calibratedSide,
                footY = foot.y,
                footX = foot.x,
                bbY = currentCalibration.bottomBracket?.y,
                crankLengthMm = currentCalibration.crankLengthMm
            )
            
            // Update latest crank angle metrics - only if tracking was successful
            if (trackingResult != null) {
                if (side == BodySide.LEFT) {
                    lastCrankAngleLeft = trackingResult.filteredAngle
                    lastInstantaneousRpmLeft = trackingResult.instantaneousRpm
                } else {
                    lastCrankAngleRight = trackingResult.filteredAngle
                    lastInstantaneousRpmRight = trackingResult.instantaneousRpm
                }
                
                // Use filtered angle for 3 O'Clock detection as well
                lastCrankAngle = trackingResult.filteredAngle
                
                Log.d(TAG, "processSideMetrics: Tracked crank angle for side $side: raw=$rawCrankAngle°, filtered=${trackingResult.filteredAngle}°, rpm=${trackingResult.instantaneousRpm}")
            }
        }
        
        // Compute KOPS using crank geometry (requires calibration and valid crank scale)
        // Only compute KOPS if we have a valid crank scale
        val kopsNormalized = if (crankScaleCache.isValid) {
            val kopsResult = KneeOverPedalOffset.computeAtFrame(poseFrame, side, currentCalibration, crankScaleCache.scale)
            if (kopsResult.isValid) kopsResult.normalizedOffset else null
        } else {
            null
        }
        
        val aggregator = if (side == BodySide.LEFT) leftCycleAggregator else rightCycleAggregator
        
        aggregator.addMeasurement(frameNumber, timestampMs, kneeAngle, hipAngle, torsoAngle, ankleAngle, kopsNormalized)
        
        // Capture key frames at critical pedal positions
        val keyFrameMap = if (side == BodySide.LEFT) leftKeyFrameSet else rightKeyFrameSet
        val sidePrefix = if (side == BodySide.LEFT) "L" else "R"
        
        // Collect 3 O'Clock frames using CrankAngleTracker (frames near 90° with high confidence)
        if (threeOClockFramesList.size < 8 && !crankScaleCache.isValid) {
            val near90 = crankAngleTracker.getMeasurementsNear(
                targetAngle = 90f,
                toleranceDegrees = 5f,
                minConfidence = 0.6f,
                side = side
            )
            
            if (near90.isNotEmpty()) {
                // Add the latest high-confidence measurement near 3 O'Clock
                val measurement = near90.last()
                threeOClockFramesList.add(poseFrame)
                Log.d(TAG, "processSideMetrics: Added frame to 3 O'Clock collection for side $side. Count: ${threeOClockFramesList.size}/8, angle=${measurement.filteredAngle}°, confidence=${measurement.confidence}")
                
                // Track best 3 O'Clock by confidence
                val bestTracker = if (side == BodySide.LEFT) leftThreeOClockBest else rightThreeOClockBest
                if (bestTracker == null || measurement.confidence > bestTracker.second) {
                    Log.d(TAG, "processSideMetrics: Found better 3 O'Clock at frame $frameNumber (confidence=${measurement.confidence}${if (bestTracker != null) ", was ${bestTracker.second} at frame ${bestTracker.first}" else ", first detection"})")
                    
                    if (side == BodySide.LEFT) {
                        leftThreeOClockBest = Pair(frameNumber, measurement.confidence)
                    } else {
                        rightThreeOClockBest = Pair(frameNumber, measurement.confidence)
                    }
                    
                    // Capture best 3 O'Clock frame
                    keyFrameMap.remove(CriticalPedalPosition.THREE_O_CLOCK)
                    val bitmap = currentFrameBitmap.copy(currentFrameBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                    keyFrameMap[CriticalPedalPosition.THREE_O_CLOCK] = Triple(frameNumber, bitmap, poseFrame.copy())
                    Log.d(TAG, "processSideMetrics: Updated 3 O'Clock frame - frameNumber=$frameNumber, confidence=${measurement.confidence}, bitmap!=null=${bitmap != null}")
                }
            }
            
            // Compute crank scale once we reach 8 frames
            if (threeOClockFramesList.size >= 8) {
                Log.d(TAG, "processSideMetrics: Computing crank scale from 8 frames for side $side")
                val newCache = KneeOverPedalOffset.computeCrankScale(threeOClockFramesList, side, currentCalibration)
                Log.d(TAG, "processSideMetrics: Crank scale computed for side $side: scale=${newCache.scale}, isValid=${newCache.isValid}, frameCount=${newCache.frameCount}")
                if (side == BodySide.LEFT) {
                    crankScaleCacheLeft = newCache
                } else {
                    crankScaleCacheRight = newCache
                }
            }
        }
        
        val events = pedalDetector.processAnklePosition(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            ankleY = ankle.y,
            visibility = ankle.visibility,
            side = side
        )
        Log.d(TAG, "processSideMetrics: Pedal events at frame $frameNumber, side $side: ${events.map { it.type }}, ankleY: ${ankle.y}, visibility: ${ankle.visibility}")
        
        for (event in events) {
            if (event.type == PedalExtremum.BDC) {
                // Seed the elliptical model from BDC/TDC spacing
                val lastTdcFrame = if (side == BodySide.LEFT) leftTdcBest?.first else rightTdcBest?.first
                if (lastTdcFrame != null && event.frameNumber > lastTdcFrame) {
                    val halfCycleFrames = (event.frameNumber - lastTdcFrame).toInt()
                    if (halfCycleFrames in 3..60) {
                        crankAngleTracker.seedFromHalfCycle(halfCycleFrames, side)
                        Log.d(TAG, "processSideMetrics: Seeded elliptical model from TDC→BDC: $halfCycleFrames frames, side $side")
                    }
                }
                // Capture BDC frame with best confidence
                val bestTracker = if (side == BodySide.LEFT) leftBdcBest else rightBdcBest
                if (bestTracker == null || event.confidence > bestTracker.second) {
                    Log.d(TAG, "Found better BDC at frame $frameNumber (confidence=${event.confidence}${if (bestTracker != null) ", was ${bestTracker.second} at frame ${bestTracker.first}" else ", first detection"})")
                    if (side == BodySide.LEFT) {
                        leftBdcBest = Pair(frameNumber, event.confidence)
                    } else {
                        rightBdcBest = Pair(frameNumber, event.confidence)
                    }
                    val bitmap = currentFrameBitmap.copy(currentFrameBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                    keyFrameMap[CriticalPedalPosition.BDC] = Triple(frameNumber, bitmap, poseFrame.copy())
                    Log.d(TAG, "Updated BDC frame - frameNumber=$frameNumber, confidence=${event.confidence}, bitmap!=null=${bitmap != null}")
                }
                
                val angleAtBdc = kneeAngle ?: 0f 
                val cycleMetrics = aggregator.endCycleAtBdc(event.frameNumber, event.timestampMs, angleAtBdc, ankleAngle)
                
                // Update overlay with latest cycle metrics
                if (cycleMetrics != null) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        val cyclesLeft = leftCycleAggregator.getCycleCount()
                        val cyclesRight = rightCycleAggregator.getCycleCount()
                        val totalCycles = cyclesLeft + cyclesRight
                        
                        cycleMetricsOverlay.updateCycleCount(totalCycles)
                        cycleMetricsOverlay.updateCycleMetrics(
                            maxExtension = cycleMetrics.kneeAngle.max,
                            minFlexion = cycleMetrics.kneeAngleAtTdc ?: cycleMetrics.kneeAngle.min
                        )
                    }
                }
            } else if (event.type == PedalExtremum.TDC) {
                // Seed the elliptical model from BDC→TDC spacing
                val lastBdcFrame = if (side == BodySide.LEFT) leftBdcBest?.first else rightBdcBest?.first
                if (lastBdcFrame != null && event.frameNumber > lastBdcFrame) {
                    val halfCycleFrames = (event.frameNumber - lastBdcFrame).toInt()
                    if (halfCycleFrames in 3..60) {
                        crankAngleTracker.seedFromHalfCycle(halfCycleFrames, side)
                        Log.d(TAG, "processSideMetrics: Seeded elliptical model from BDC→TDC: $halfCycleFrames frames, side $side")
                    }
                }
                // Capture TDC frame with best confidence
                val bestTracker = if (side == BodySide.LEFT) leftTdcBest else rightTdcBest
                if (bestTracker == null || event.confidence > bestTracker.second) {
                    Log.d(TAG, "Found better TDC at frame $frameNumber (confidence=${event.confidence}${if (bestTracker != null) ", was ${bestTracker.second} at frame ${bestTracker.first}" else ", first detection"})")
                    if (side == BodySide.LEFT) {
                        leftTdcBest = Pair(frameNumber, event.confidence)
                    } else {
                        rightTdcBest = Pair(frameNumber, event.confidence)
                    }
                    val bitmap = currentFrameBitmap.copy(currentFrameBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                    keyFrameMap[CriticalPedalPosition.TDC] = Triple(frameNumber, bitmap, poseFrame.copy())
                    Log.d(TAG, "Updated TDC frame - frameNumber=$frameNumber, confidence=${event.confidence}, bitmap!=null=${bitmap != null}")
                }
                
                // Record TDC for the aggregator (including hip angle at TDC)
                aggregator.recordTdc(kneeAngle, hipAngle)
            }
        }
    }
    
    private fun finishAnalysis() {
        val leftSummary = leftCycleAggregator.getSummary()
        val rightSummary = rightCycleAggregator.getSummary()
        
        val cycleSummary = if (leftSummary.cycleCount >= rightSummary.cycleCount) {
            leftSummary
        } else {
            rightSummary
        }

        // Must run on engine
        val calibration = currentCalibration
        if (!calibration.isComplete) {
             // Should not happen if UI is correct
             Toast.makeText(this, "Calibration incomplete", Toast.LENGTH_SHORT).show()
             return
        }

        val input = FitAnalysisInput(
            cycleSummary = cycleSummary,
            bikeCalibration = calibration
        )
        
        // Create engine with context-aware thresholds
        val config = FitEngineConfig.forContext(selectedContext, selectedBias)
        val engine = FitEngine(config)
        val result = engine.analyze(input)
        
        // Create KeyFrameSets from captured frames with pose and angles data
        val leftFrameSet = createKeyFrameSet(leftKeyFrameSet, BodySide.LEFT)
        val rightFrameSet = createKeyFrameSet(rightKeyFrameSet, BodySide.RIGHT)
        
        Log.d(TAG, "finishAnalysis: leftFrameSet = $leftFrameSet, rightFrameSet = $rightFrameSet")
        Log.d(TAG, "finishAnalysis: leftFrameSet.hasAnyFrames() = ${leftFrameSet.hasAnyFrames()}, rightFrameSet.hasAnyFrames() = ${rightFrameSet.hasAnyFrames()}")
        
        // Create analysis result with key frames
        val resultWithKeyFrames = result.copy(
            keyFrameSetLeft = leftFrameSet,
            keyFrameSetRight = rightFrameSet
        )
        
        Log.d(TAG, "finishAnalysis: resultWithKeyFrames.keyFrameSetLeft = ${resultWithKeyFrames.keyFrameSetLeft}, resultWithKeyFrames.keyFrameSetRight = ${resultWithKeyFrames.keyFrameSetRight}")
        
        val summary = FitSummary.fromAnalysisResult(resultWithKeyFrames, selectedContext, selectedBias)
        
        FitSummaryActivity.start(this, summary, resultWithKeyFrames)
        finish() 
    }
    
    /**
     * Creates a KeyFrameSet from captured key frame data.
     * 
     * @param capturedFrames Map of CriticalPedalPosition to frame data (frameNum, bitmap, poseFrame)
     * @param side Body side
     * @return KeyFrameSet with captured frames
     */
    private fun createKeyFrameSet(
        capturedFrames: Map<CriticalPedalPosition, Triple<Long, Bitmap?, PoseFrame?>>,
        side: BodySide
    ): KeyFrameSet {
        val tdcFrame = capturedFrames[CriticalPedalPosition.TDC]?.let { (frameNum, bitmap, poseFrame) ->
            KeyFrameDataPoint(
                frameNumber = frameNum,
                timestampMs = frameNum * 33, // Approximate timestamp
                position = CriticalPedalPosition.TDC,
                bitmap = bitmap,
                poseFrame = poseFrame,
                side = side
            )
        }
        
        val bdcFrame = capturedFrames[CriticalPedalPosition.BDC]?.let { (frameNum, bitmap, poseFrame) ->
            KeyFrameDataPoint(
                frameNumber = frameNum,
                timestampMs = frameNum * 33, // Approximate timestamp
                position = CriticalPedalPosition.BDC,
                bitmap = bitmap,
                poseFrame = poseFrame,
                side = side
            )
        }
        
        val threeOClockFrame = capturedFrames[CriticalPedalPosition.THREE_O_CLOCK]?.let { (frameNum, bitmap, poseFrame) ->
            Log.d(TAG, "createKeyFrameSet: THREE_O_CLOCK frameNum=$frameNum, bitmap!=null=${bitmap != null}, poseFrame!=null=${poseFrame != null}")
            KeyFrameDataPoint(
                frameNumber = frameNum,
                timestampMs = frameNum * 33, // Approximate timestamp
                position = CriticalPedalPosition.THREE_O_CLOCK,
                bitmap = bitmap,
                poseFrame = poseFrame,
                side = side
            )
        }
        
        return KeyFrameSet(
            tdcFrame = tdcFrame,
            bdcFrame = bdcFrame,
            threeOClockFrame = threeOClockFrame,
            side = side
        )
    }

    private fun getBitmapRect(imageView: ImageView): RectF? {
        val drawable = imageView.drawable ?: return null
        val matrix = imageView.imageMatrix
        
        val rect = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        matrix.mapRect(rect)
        return rect
    }

    override fun onDestroy() {
        super.onDestroy()
        poseLandmarkerWrapper?.close()
    }
}
