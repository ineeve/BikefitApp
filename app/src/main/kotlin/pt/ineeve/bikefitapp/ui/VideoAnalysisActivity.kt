package pt.ineeve.bikefitapp.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
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
import pt.ineeve.bikefitapp.fit.FitEngine
import pt.ineeve.bikefitapp.fit.FitSummary
import pt.ineeve.bikefitapp.pose.*
import com.google.android.material.button.MaterialButton
import com.google.mediapipe.tasks.vision.core.RunningMode
import pt.ineeve.bikefitapp.ui.AngleDisplay

class VideoAnalysisActivity : AppCompatActivity() {

    private lateinit var videoFrameView: ImageView
    private lateinit var calibrationOverlay: CalibrationOverlayView
    private lateinit var poseOverlay: PoseOverlayView
    private lateinit var cycleMetricsOverlay: CycleMetricsOverlayView
    private lateinit var actionButton: MaterialButton
    private lateinit var progressContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

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

        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (uriString == null) {
            finish()
            return
        }
        videoUri = Uri.parse(uriString)

        setupCalibrationUI()
        loadFirstFrame()
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
                actionButton.text = "Start Analysis" // or "Confirm"
                actionButton.visibility = View.VISIBLE
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
             // Calculate knee angles for display
             val angleDisplays = calculateKneeAngles(poseResult)
             
             withContext(Dispatchers.Main) {
                 poseOverlay.setImageSourceInfo(bitmap.width, bitmap.height)
                 poseOverlay.updatePose(poseResult)
                 poseOverlay.updateAngles(angleDisplays)
                 
                 // Update cycle count
                 val cyclesLeft = leftCycleAggregator.getCycleCount()
                 val cyclesRight = rightCycleAggregator.getCycleCount()
                 val totalCycles = cyclesLeft + cyclesRight
                 cycleMetricsOverlay.updateCycleCount(totalCycles)
                 
                 // Update current knee angle (prioritize Right if visible, else Left)
                 val displayAngle = angleDisplays.firstOrNull { it.label == "R" } 
                     ?: angleDisplays.firstOrNull { it.label == "L" }
                 
                 if (displayAngle != null) {
                     cycleMetricsOverlay.updateCurrentKneeAngle(displayAngle.angle)
                 }
                 
                 // Update hip angle
                 val hipResult = HipAngleCalculator.calculateHipAngle(poseResult, BodySide.RIGHT)
                 if (hipResult.isValid) {
                     cycleMetricsOverlay.updateCurrentHipAngle(hipResult.angle)
                 }
                 
                 // Update torso angle
                 val torsoResult = TorsoAngleCalculator.calculateTorsoAngle(poseResult, BodySide.RIGHT)
                 if (torsoResult.isValid) {
                     cycleMetricsOverlay.updateCurrentTorsoAngle(torsoResult.angle)
                 }
             }
             
             processSideMetrics(poseResult, BodySide.RIGHT, timestampMs, frameNumber)
             processSideMetrics(poseResult, BodySide.LEFT, timestampMs, frameNumber)
         }
    }

    private fun calculateKneeAngles(poseResult: PoseResult): List<AngleDisplay> {
        val angles = mutableListOf<AngleDisplay>()
        
        val leftKnee = KneeAngleCalculator.calculateKneeAngle(poseResult, BodySide.LEFT)
        if (leftKnee.isValid) {
            angles.add(AngleDisplay(
                angle = leftKnee.angle,
                landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
                isValid = true,
                label = "L"
            ))
        }
        
        val rightKnee = KneeAngleCalculator.calculateKneeAngle(poseResult, BodySide.RIGHT)
        if (rightKnee.isValid) {
            angles.add(AngleDisplay(
                angle = rightKnee.angle,
                landmarkIndex = PoseLandmarkIndex.RIGHT_KNEE,
                isValid = true,
                label = "R"
            ))
        }
        
        return angles
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
        
        val aggregator = if (side == BodySide.LEFT) leftCycleAggregator else rightCycleAggregator
        
        aggregator.addMeasurement(frameNumber, timestampMs, kneeAngle, hipAngle, torsoAngle)
        
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
                val cycleMetrics = aggregator.endCycleAtBdc(event.frameNumber, event.timestampMs, angleAtBdc)
                
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
        
        val engine = FitEngine()
        val result = engine.analyze(input)
        val summary = FitSummary.fromAnalysisResult(result)
        
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
