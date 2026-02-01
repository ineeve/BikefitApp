package pt.ineeve.bikefitapp.pose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Wrapper for MediaPipe Pose Landmarker.
 * 
 * This class encapsulates the MediaPipe Pose Landmarker API and provides
 * a simplified interface for pose estimation from Bitmap images.
 * 
 * Usage:
 * ```
 * val wrapper = PoseLandmarkerWrapper(context)
 * val result = wrapper.detectPose(bitmap, timestampMs)
 * if (result.isValid) {
 *     val hip = result.getLandmark(PoseLandmarkIndex.LEFT_HIP)
 *     // Process landmarks...
 * }
 * wrapper.close()
 * ```
 * 
 * Thread Safety: The detect methods should be called from a single thread.
 * For camera integration, use the VIDEO running mode with detectPoseForVideo().
 */
class PoseLandmarkerWrapper(
    context: Context,
    private val runningMode: RunningMode = RunningMode.VIDEO,
    private val minPoseDetectionConfidence: Float = DEFAULT_DETECTION_CONFIDENCE,
    private val minPosePresenceConfidence: Float = DEFAULT_PRESENCE_CONFIDENCE,
    private val minTrackingConfidence: Float = DEFAULT_TRACKING_CONFIDENCE,
    private val resultListener: ((PoseResult) -> Unit)? = null
) : AutoCloseable {

    private var poseLandmarker: PoseLandmarker? = null
    private var isInitialized = false
    private var initializationError: Exception? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(runningMode)
                .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                .setMinPosePresenceConfidence(minPosePresenceConfidence)
                .setMinTrackingConfidence(minTrackingConfidence)
                .setNumPoses(1) // We only need one pose for bike fit

            // Set result listener for LIVE_STREAM mode
            if (runningMode == RunningMode.LIVE_STREAM && resultListener != null) {
                optionsBuilder.setResultListener { result, _ ->
                    val poseResult = convertToPoseResult(result, System.currentTimeMillis())
                    resultListener.invoke(poseResult)
                }
                optionsBuilder.setErrorListener { error ->
                    Log.e(TAG, "MediaPipe error: ${error.message}", error)
                }
            }

            poseLandmarker = PoseLandmarker.createFromOptions(context, optionsBuilder.build())
            isInitialized = true
            Log.d(TAG, "PoseLandmarker initialized successfully with mode: $runningMode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PoseLandmarker", e)
            initializationError = e
            isInitialized = false
        }
    }

    /**
     * Checks if the PoseLandmarker was initialized successfully.
     */
    fun isReady(): Boolean = isInitialized && poseLandmarker != null

    /**
     * Gets the initialization error if initialization failed.
     */
    fun getInitializationError(): Exception? = initializationError

    /**
     * Detects pose landmarks in a Bitmap image.
     * 
     * Use this method for single image processing (IMAGE running mode).
     * 
     * @param bitmap The input image
     * @param timestampMs Optional timestamp for the result
     * @return PoseResult containing detected landmarks
     */
    fun detectPose(bitmap: Bitmap, timestampMs: Long = System.currentTimeMillis()): PoseResult {
        if (!isReady()) {
            Log.w(TAG, "PoseLandmarker not ready, returning empty result")
            return PoseResult.EMPTY
        }

        if (runningMode != RunningMode.IMAGE) {
            Log.w(TAG, "detectPose() should be used with IMAGE running mode. " +
                    "Use detectPoseForVideo() for VIDEO mode or detectPoseAsync() for LIVE_STREAM.")
        }

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = poseLandmarker?.detect(mpImage)
            convertToPoseResult(result, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting pose", e)
            PoseResult.EMPTY
        }
    }

    /**
     * Detects pose landmarks for video frame processing.
     * 
     * Use this method for video frames (VIDEO running mode).
     * Timestamps must be monotonically increasing.
     * 
     * @param bitmap The input frame
     * @param timestampMs Frame timestamp in milliseconds (must be monotonically increasing)
     * @return PoseResult containing detected landmarks
     */
    fun detectPoseForVideo(bitmap: Bitmap, timestampMs: Long): PoseResult {
        if (!isReady()) {
            Log.w(TAG, "PoseLandmarker not ready, returning empty result")
            return PoseResult.EMPTY
        }

        if (runningMode != RunningMode.VIDEO) {
            Log.w(TAG, "detectPoseForVideo() should be used with VIDEO running mode")
        }

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = poseLandmarker?.detectForVideo(mpImage, timestampMs)
            convertToPoseResult(result, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting pose for video frame at $timestampMs", e)
            PoseResult.EMPTY
        }
    }

    /**
     * Detects pose landmarks asynchronously for live stream processing.
     * 
     * Use this method for camera live stream (LIVE_STREAM running mode).
     * Results are delivered via the resultListener callback.
     * 
     * @param bitmap The input frame
     * @param timestampMs Frame timestamp in milliseconds (must be monotonically increasing)
     */
    fun detectPoseAsync(bitmap: Bitmap, timestampMs: Long) {
        if (!isReady()) {
            Log.w(TAG, "PoseLandmarker not ready, skipping async detection")
            return
        }

        if (runningMode != RunningMode.LIVE_STREAM) {
            Log.w(TAG, "detectPoseAsync() should be used with LIVE_STREAM running mode")
            return
        }

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting async pose detection at $timestampMs", e)
        }
    }

    /**
     * Converts MediaPipe result to our PoseResult format.
     */
    private fun convertToPoseResult(result: PoseLandmarkerResult?, timestampMs: Long): PoseResult {
        if (result == null || result.landmarks().isEmpty()) {
            return PoseResult(
                landmarks = emptyList(),
                timestampMs = timestampMs,
                isValid = false
            )
        }

        // Get the first detected pose (we only track one person for bike fit)
        val mpLandmarks = result.landmarks()[0]
        
        val landmarks = mpLandmarks.map { mpLandmark ->
            Landmark(
                x = mpLandmark.x(),
                y = mpLandmark.y(),
                z = mpLandmark.z(),
                visibility = mpLandmark.visibility().orElse(0f),
                presence = mpLandmark.presence().orElse(0f)
            )
        }

        // Calculate overall confidence as average visibility of all landmarks
        val confidence = if (landmarks.isNotEmpty()) {
            landmarks.map { it.visibility }.average().toFloat()
        } else {
            0f
        }

        return PoseResult(
            landmarks = landmarks,
            timestampMs = timestampMs,
            isValid = true,
            confidence = confidence
        )
    }

    /**
     * Releases the PoseLandmarker resources.
     * Should be called when the wrapper is no longer needed.
     */
    override fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
        isInitialized = false
        Log.d(TAG, "PoseLandmarker closed")
    }

    companion object {
        private const val TAG = "PoseLandmarkerWrapper"
        
        /** Path to the pose landmarker model in assets */
        private const val MODEL_ASSET_PATH = "pose_landmarker_heavy.task"
        
        /** Default minimum confidence for pose detection */
        const val DEFAULT_DETECTION_CONFIDENCE = 0.5f
        
        /** Default minimum confidence for pose presence */
        const val DEFAULT_PRESENCE_CONFIDENCE = 0.5f
        
        /** Default minimum confidence for pose tracking */
        const val DEFAULT_TRACKING_CONFIDENCE = 0.5f
    }
}
