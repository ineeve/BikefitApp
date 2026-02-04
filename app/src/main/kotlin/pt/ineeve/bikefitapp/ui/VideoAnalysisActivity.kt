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
    
    // Key frame capture for 3 critical positions
    private val leftKeyFrameSet = mutableMapOf<CriticalPedalPosition, Triple<Long, Bitmap?, PoseFrame?>>()
    private val rightKeyFrameSet = mutableMapOf<CriticalPedalPosition, Triple<Long, Bitmap?, PoseFrame?>>()
    
    // Track captured frames to prevent duplicates
    private val capturedFrameNumbers = mutableSetOf<String>()
    
    // Video info
    private var videoDurationMs = 0L

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        private const val TAG = "VideoAnalysisActivity"
        private const val MAX_CYCLES_TO_COLLECT = 10
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

        val point = BikeReferencePoint(type, imageNormX, imageNormY)
        currentCalibration = currentCalibration.withPoint(point)
        
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
        val point = BikeReferencePoint(type, imageNormX, imageNormY)
        currentCalibration = currentCalibration.withPoint(point)
        
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
            is CalibrationState.WaitingForBottomBracket -> CalibrationState.WaitingForHandlebar
            is CalibrationState.WaitingForHandlebar -> {
                // Show crank length input dialog
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
        
        // Estimate frames
        val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
        val frameCount = frameCountStr?.toIntOrNull() ?: ((videoDurationMs / 33).toInt())
        
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
                     retriever.getFrameAtTime(i * 33333L, MediaMetadataRetriever.OPTION_CLOSEST)
                }
            } catch (e: Exception) { null }

            if (frame != null) {
                withContext(Dispatchers.Main) {
                    videoFrameView.setImageBitmap(frame)
                }
                val timestampMs = (i * 33).toLong() // Approx timestamp if not available from frame
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
         val poseResult = poseLandmarkerWrapper?.detectPoseForVideo(bitmap, timestampMs) ?: PoseResult.EMPTY
         
         if (poseResult.isValid) {
             // Calculate all angles for display
             val angleDisplays = calculateAllAngles(poseResult)
             
             withContext(Dispatchers.Main) {
                 poseOverlay.setImageSourceInfo(bitmap.width, bitmap.height)
                 poseOverlay.updatePose(poseResult)
                 poseOverlay.updateAngles(angleDisplays)
                 
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
             }
             
             // Only process cycle metrics for the dominant side
             val dominantSide = detectDominantSide(poseResult)
             processSideMetrics(poseResult, dominantSide, timestampMs, frameNumber)
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

    private fun processSideMetrics(poseResult: PoseResult, side: BodySide, timestampMs: Long, frameNumber: Long) {
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
        
        // Compute KOPS (Knee Over Pedal Spindle) normalized value
        val poseFrame = PoseFrame(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            landmarks = poseResult.landmarks,
            confidence = poseResult.confidence
        )
        val kopsResult = KneeOverPedalOffset.computeAtFrame(poseFrame, side)
        val kopsNormalized = if (kopsResult.isValid) kopsResult.normalizedOffset else null
        
        val aggregator = if (side == BodySide.LEFT) leftCycleAggregator else rightCycleAggregator
        
        aggregator.addMeasurement(frameNumber, timestampMs, kneeAngle, hipAngle, torsoAngle, ankleAngle, kopsNormalized)
        
        // Capture key frames at critical pedal positions
        val keyFrameMap = if (side == BodySide.LEFT) leftKeyFrameSet else rightKeyFrameSet
        val sidePrefix = if (side == BodySide.LEFT) "L" else "R"
        
        // Check for 3 O'Clock position (pedal at horizontal)
        Log.d(TAG, "processSideMetrics: Checking 3 O'Clock detection for frame $frameNumber, side $side, poseFrame valid: ${poseFrame.isValid}, landmarks: ${poseFrame.landmarks.size}")
        val threeOClockEvent = ThreeOClockDetector.detectAtFrame(poseFrame, side)
        Log.d(TAG, "processSideMetrics: 3 O'Clock detection result: $threeOClockEvent, already captured: ${keyFrameMap.containsKey(CriticalPedalPosition.THREE_O_CLOCK)}")
        
        if (threeOClockEvent != null && !keyFrameMap.containsKey(CriticalPedalPosition.THREE_O_CLOCK)) {
            val frameKey = "${sidePrefix}_3OCLOCK_${frameNumber}"
            if (!capturedFrameNumbers.contains(frameKey)) {
                Log.d(TAG, "processSideMetrics: Capturing 3 O'Clock frame at $frameNumber, storing frameNumber=$frameNumber")
                val bitmap = currentVideoFrameBitmap?.let {
                    // Create a completely independent copy
                    val copy = it.copy(it.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                    copy
                }
                Log.d(TAG, "processSideMetrics: Before storing 3 O'Clock - frameNumber=$frameNumber, bitmap!=null=${bitmap != null}")
                keyFrameMap[CriticalPedalPosition.THREE_O_CLOCK] = Triple(frameNumber, bitmap, poseFrame.copy())
                capturedFrameNumbers.add(frameKey)
                Log.d(TAG, "processSideMetrics: After storing 3 O'Clock - keyFrameMap[THREE_O_CLOCK]?.first=${keyFrameMap[CriticalPedalPosition.THREE_O_CLOCK]?.first}")
                Log.d(TAG, "Captured 3 O'Clock frame at frame $frameNumber, side $side, bitmap: ${bitmap != null}")
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
                // Capture BDC frame if not already captured
                if (!keyFrameMap.containsKey(CriticalPedalPosition.BDC)) {
                    val frameKey = "${sidePrefix}_BDC_${frameNumber}"
                    if (!capturedFrameNumbers.contains(frameKey)) {
                        val bitmap = currentVideoFrameBitmap?.let {
                            // Create a completely independent copy
                            it.copy(it.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                        }
                        keyFrameMap[CriticalPedalPosition.BDC] = Triple(frameNumber, bitmap, poseFrame.copy())
                        capturedFrameNumbers.add(frameKey)
                        Log.d(TAG, "Captured BDC frame at frame $frameNumber, side $side, bitmap: ${bitmap != null}")
                    }
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
                // Capture TDC frame if not already captured
                if (!keyFrameMap.containsKey(CriticalPedalPosition.TDC)) {
                    val frameKey = "${sidePrefix}_TDC_${frameNumber}"
                    if (!capturedFrameNumbers.contains(frameKey)) {
                        val bitmap = currentVideoFrameBitmap?.let {
                            // Create a completely independent copy
                            it.copy(it.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                        }
                        keyFrameMap[CriticalPedalPosition.TDC] = Triple(frameNumber, bitmap, poseFrame.copy())
                        capturedFrameNumbers.add(frameKey)
                        Log.d(TAG, "Captured TDC frame at frame $frameNumber, side $side, bitmap: ${bitmap != null}")
                    }
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
