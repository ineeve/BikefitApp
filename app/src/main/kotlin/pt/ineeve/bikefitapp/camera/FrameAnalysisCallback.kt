package pt.ineeve.bikefitapp.camera

import android.graphics.Bitmap

/**
 * Callback interface for receiving camera frames for analysis.
 * 
 * Implementations should process frames quickly to avoid blocking
 * the camera pipeline. Heavy processing should be offloaded to
 * another thread or use frame sampling.
 */
fun interface FrameAnalysisCallback {
    /**
     * Called when a new frame is available for analysis.
     * 
     * @param bitmap The camera frame as a Bitmap (ARGB_8888)
     * @param timestampMs The frame timestamp in milliseconds
     * @param rotationDegrees The rotation needed to display the image correctly
     */
    fun onFrameAvailable(bitmap: Bitmap, timestampMs: Long, rotationDegrees: Int)
}
