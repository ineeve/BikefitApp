package pt.ineeve.bikefitapp.camera

/**
 * Time-based frame sampler that limits frame processing to a target FPS.
 * 
 * Frames arriving faster than the target rate are dropped to reduce CPU load
 * during pose estimation and other heavy processing.
 * 
 * @param targetFps The target frames per second (default: 10 FPS)
 */
class FrameSampler(targetFps: Float = DEFAULT_TARGET_FPS) {

    companion object {
        /** Default target FPS for frame sampling */
        const val DEFAULT_TARGET_FPS = 10f
        
        /** Minimum allowed FPS to prevent invalid configurations */
        const val MIN_FPS = 1f
        
        /** Maximum allowed FPS (essentially no throttling) */
        const val MAX_FPS = 60f
    }

    /** Minimum interval between frames in milliseconds */
    private var minIntervalMs: Long = calculateInterval(targetFps)
    
    /** Timestamp of the last accepted frame in milliseconds */
    private var lastFrameTimestampMs: Long = Long.MIN_VALUE
    
    /** Current target FPS */
    var targetFps: Float = targetFps
        private set

    /**
     * Checks if a frame should be processed based on its timestamp.
     * 
     * @param timestampMs The frame timestamp in milliseconds
     * @return true if the frame should be processed, false if it should be dropped
     */
    fun shouldProcessFrame(timestampMs: Long): Boolean {
        // First frame is always processed
        if (lastFrameTimestampMs == Long.MIN_VALUE) {
            lastFrameTimestampMs = timestampMs
            return true
        }
        
        val elapsed = timestampMs - lastFrameTimestampMs
        
        return if (elapsed >= minIntervalMs) {
            lastFrameTimestampMs = timestampMs
            true
        } else {
            false
        }
    }

    /**
     * Updates the target FPS for sampling.
     * 
     * @param fps The new target FPS (clamped to MIN_FPS..MAX_FPS)
     */
    fun setTargetFps(fps: Float) {
        targetFps = fps.coerceIn(MIN_FPS, MAX_FPS)
        minIntervalMs = calculateInterval(targetFps)
    }

    /**
     * Resets the sampler state.
     * Call this when starting a new recording session.
     */
    fun reset() {
        lastFrameTimestampMs = Long.MIN_VALUE
    }

    /**
     * Calculates the minimum interval between frames for a given FPS.
     */
    private fun calculateInterval(fps: Float): Long {
        val clampedFps = fps.coerceIn(MIN_FPS, MAX_FPS)
        return (1000f / clampedFps).toLong()
    }
}
