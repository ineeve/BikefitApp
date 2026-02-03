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

    // Analysis
    private val pedalDetector = PedalCycleDetector()
    private val leftCycleAggregator = CycleAggregator(BodySide.LEFT)
    private val rightCycleAggregator = CycleAggregator(BodySide.RIGHT)
    
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
        poseOverlay = findViewById(R.id.pose_overlay)
        poseOverlay.scaleType = PoseOverlayView.ScaleType.FIT_CENTER
        cycleMetricsOverlay = findViewById(R.id.cycle_metrics_overlay)
        
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
            handleCalibrationAdjustment(type, x, y)
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

    private fun handleCalibrationTap(viewNormX: Float, viewNormY: Float) {
        // 1. De-normalize view coords to pixels
        val viewX = viewNormX * calibrationOverlay.width
        val viewY = viewNormY * calibrationOverlay.height

        // 2. Get image rect inside ImageView
        val imageRect = getBitmapRect(videoFrameView) ?: return
        
        // 3. Check if tap is inside image
        if (!imageRect.contains(viewX, viewY)) return

        // 4. Normalize relative to image
        val imageNormX = (viewX - imageRect.left) / imageRect.width()
        val imageNormY = (viewY - imageRect.top) / imageRect.height()

        val type = calibrationState.getCurrentPointType() ?: return

        val point = BikeReferencePoint(type, imageNormX, imageNormY)
        currentCalibration = currentCalibration.withPoint(point)
        
        // Update state
        proceedCalibrationState()
        
        // Update UI
        updateOverlay()
    }

    private fun handleCalibrationAdjustment(type: BikeReferencePointType, viewNormX: Float, viewNormY: Float) {
        val viewX = viewNormX * calibrationOverlay.width
        val viewY = viewNormY * calibrationOverlay.height
        
        val imageRect = getBitmapRect(videoFrameView) ?: return
        
        // Clamp to image area
        val clampedX = viewX.coerceIn(imageRect.left, imageRect.right)
        val clampedY = viewY.coerceIn(imageRect.top, imageRect.bottom)
        
        val imageNormX = (clampedX - imageRect.left) / imageRect.width()
        val imageNormY = (clampedY - imageRect.top) / imageRect.height()
        
        val point = BikeReferencePoint(type, imageNormX, imageNormY)
        currentCalibration = currentCalibration.withPoint(point)
        
        updateOverlay()
    }
    
    // Map Image-Relative coordinates to View-Relative for correct display on Overlay
    private fun updateOverlay() {
        val imageRect = getBitmapRect(videoFrameView)
        
        if (imageRect == null) {
             calibrationOverlay.setCalibration(currentCalibration)
             calibrationOverlay.setState(calibrationState)
             return
        }

        val w = calibrationOverlay.width.toFloat()
        val h = calibrationOverlay.height.toFloat()
        if (w == 0f || h == 0f) return

        var viewCalibration = BikeCalibration.EMPTY
        
        currentCalibration.saddleTop?.let { p ->
             viewCalibration = viewCalibration.withPoint(mapToView(p, imageRect, w, h))
        }
        currentCalibration.bottomBracket?.let { p ->
             viewCalibration = viewCalibration.withPoint(mapToView(p, imageRect, w, h))
        }
        currentCalibration.handlebar?.let { p ->
             viewCalibration = viewCalibration.withPoint(mapToView(p, imageRect, w, h))
        }

        calibrationOverlay.setCalibration(viewCalibration)
        calibrationOverlay.setState(calibrationState)
    }

    private fun mapToView(point: BikeReferencePoint, rect: RectF, viewW: Float, viewH: Float): BikeReferencePoint {
        val vx = (point.x * rect.width() + rect.left) / viewW
        val vy = (point.y * rect.height() + rect.top) / viewH
        return BikeReferencePoint(point.type, vx, vy)
    }

    private fun proceedCalibrationState() {
        calibrationState = when (calibrationState) {
            is CalibrationState.WaitingForSaddle -> CalibrationState.WaitingForBottomBracket
            is CalibrationState.WaitingForBottomBracket -> CalibrationState.WaitingForHandlebar
            is CalibrationState.WaitingForHandlebar -> {
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
        val heelIndex = if (dominantSide == BodySide.LEFT) PoseLandmarkIndex.LEFT_HEEL else PoseLandmarkIndex.RIGHT_HEEL
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
     * Detects which body side is more visible to the camera.
     * 
     * Compares the average visibility of key landmarks on each side
     * to determine which side the user is presenting to the camera.
     * 
     * @param poseResult The pose detection result
     * @return The body side with higher average visibility
     */
    private fun detectDominantSide(poseResult: PoseResult): BodySide {
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
        
        val events = pedalDetector.processAnklePosition(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            ankleY = ankle.y,
            visibility = ankle.visibility,
            side = side
        )
        
        for (event in events) {
            if (event.type == PedalExtremum.BDC) {
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
                            maxExtension = cycleMetrics.kneeAngleAtBdc ?: cycleMetrics.kneeAngle.max,
                            minFlexion = cycleMetrics.kneeAngleAtTdc ?: cycleMetrics.kneeAngle.min
                        )
                    }
                }
            } else if (event.type == PedalExtremum.TDC) {
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
        val summary = FitSummary.fromAnalysisResult(result, selectedContext, selectedBias)
        
        FitSummaryActivity.start(this, summary)
        finish() 
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
