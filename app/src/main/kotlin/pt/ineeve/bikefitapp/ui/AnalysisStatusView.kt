package pt.ineeve.bikefitapp.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import pt.ineeve.bikefitapp.R

/**
 * Represents the current analysis status.
 */
enum class AnalysisStatus {
    /** Everything is working correctly */
    OK,
    
    /** Pose detection confidence is too low */
    LOW_CONFIDENCE,
    
    /** Required landmarks are not visible */
    MISSING_LANDMARKS,
    
    /** Bike calibration is incomplete or invalid */
    BAD_CALIBRATION,
    
    /** No person detected in frame */
    NO_PERSON_DETECTED,
    
    /** Camera or processing error */
    ERROR
}

/**
 * Data class representing a status message to display.
 * 
 * @param status The analysis status type
 * @param messageResId String resource ID for the message
 * @param actionResId Optional string resource ID for action button text
 * @param iconResId Optional drawable resource ID for icon
 */
data class StatusMessage(
    val status: AnalysisStatus,
    val messageResId: Int,
    val actionResId: Int = 0,
    val iconResId: Int = R.drawable.ic_warning
) {
    companion object {
        fun lowConfidence() = StatusMessage(
            status = AnalysisStatus.LOW_CONFIDENCE,
            messageResId = R.string.status_low_confidence,
            iconResId = R.drawable.ic_warning
        )
        
        fun missingLandmarks() = StatusMessage(
            status = AnalysisStatus.MISSING_LANDMARKS,
            messageResId = R.string.status_missing_landmarks,
            iconResId = R.drawable.ic_warning
        )
        
        fun badCalibration() = StatusMessage(
            status = AnalysisStatus.BAD_CALIBRATION,
            messageResId = R.string.status_bad_calibration,
            actionResId = R.string.status_action_recalibrate,
            iconResId = R.drawable.ic_error
        )
        
        fun noPersonDetected() = StatusMessage(
            status = AnalysisStatus.NO_PERSON_DETECTED,
            messageResId = R.string.status_no_person,
            iconResId = R.drawable.ic_warning
        )
        
        fun error(messageResId: Int = R.string.status_error) = StatusMessage(
            status = AnalysisStatus.ERROR,
            messageResId = messageResId,
            iconResId = R.drawable.ic_error
        )
    }
}

/**
 * A non-blocking status view that displays analysis warnings and errors.
 * 
 * Features:
 * - Shows status messages at top of screen
 * - Automatically hides after timeout for warnings
 * - Stays visible for critical errors until dismissed
 * - Optional action button for recovery actions
 * - Smooth fade in/out animations
 * 
 * Usage:
 * ```kotlin
 * val statusView = findViewById<AnalysisStatusView>(R.id.analysis_status)
 * statusView.showStatus(StatusMessage.lowConfidence())
 * statusView.hideStatus()
 * ```
 */
class AnalysisStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ==================== Configuration ====================

    /** Duration to show warning messages before auto-hide */
    var warningDisplayDuration: Long = DEFAULT_WARNING_DURATION
    
    /** Duration for fade animations */
    var fadeAnimationDuration: Long = DEFAULT_FADE_DURATION
    
    /** Whether to auto-hide warning messages */
    var autoHideWarnings: Boolean = true

    // ==================== Views ====================

    private val containerView: View
    private val iconView: ImageView
    private val messageView: TextView
    private val actionButton: TextView

    // ==================== State ====================

    private var currentStatus: StatusMessage? = null
    private var isShowing = false
    private val hideRunnable = Runnable { hideStatus() }
    
    /** Callback when action button is clicked */
    var onActionClickListener: ((AnalysisStatus) -> Unit)? = null

    // ==================== Initialization ====================

    init {
        LayoutInflater.from(context).inflate(R.layout.view_analysis_status, this, true)
        
        containerView = findViewById(R.id.status_container)
        iconView = findViewById(R.id.status_icon)
        messageView = findViewById(R.id.status_message)
        actionButton = findViewById(R.id.status_action)
        
        actionButton.setOnClickListener {
            currentStatus?.let { status ->
                onActionClickListener?.invoke(status.status)
            }
        }
        
        // Start hidden
        alpha = 0f
        visibility = View.GONE
    }

    // ==================== Public API ====================

    /**
     * Shows a status message.
     * 
     * @param message The status message to display
     */
    fun showStatus(message: StatusMessage) {
        // Cancel any pending hide
        handler?.removeCallbacks(hideRunnable)
        
        currentStatus = message
        
        // Update views
        messageView.setText(message.messageResId)
        iconView.setImageResource(message.iconResId)
        
        // Set background color based on severity
        val backgroundRes = when (message.status) {
            AnalysisStatus.ERROR, AnalysisStatus.BAD_CALIBRATION -> R.drawable.bg_status_error
            else -> R.drawable.bg_status_warning
        }
        containerView.setBackgroundResource(backgroundRes)
        
        // Show/hide action button
        if (message.actionResId != 0) {
            actionButton.setText(message.actionResId)
            actionButton.visibility = View.VISIBLE
        } else {
            actionButton.visibility = View.GONE
        }
        
        // Show with animation
        if (!isShowing) {
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(fadeAnimationDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isShowing = true
                        scheduleAutoHide(message)
                    }
                })
                .start()
        } else {
            // Already showing, just update content and reschedule hide
            scheduleAutoHide(message)
        }
    }

    /**
     * Shows a low confidence warning.
     */
    fun showLowConfidence() {
        showStatus(StatusMessage.lowConfidence())
    }

    /**
     * Shows a missing landmarks warning.
     */
    fun showMissingLandmarks() {
        showStatus(StatusMessage.missingLandmarks())
    }

    /**
     * Shows a bad calibration error.
     */
    fun showBadCalibration() {
        showStatus(StatusMessage.badCalibration())
    }

    /**
     * Shows a no person detected warning.
     */
    fun showNoPersonDetected() {
        showStatus(StatusMessage.noPersonDetected())
    }

    /**
     * Hides the status view with animation.
     */
    fun hideStatus() {
        if (!isShowing && visibility != View.VISIBLE) return
        
        handler?.removeCallbacks(hideRunnable)
        
        animate()
            .alpha(0f)
            .setDuration(fadeAnimationDuration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    isShowing = false
                    currentStatus = null
                }
            })
            .start()
    }

    /**
     * Returns the current status, or null if not showing.
     */
    fun getCurrentStatus(): AnalysisStatus? = currentStatus?.status

    /**
     * Returns true if the status view is currently visible.
     */
    fun isStatusShowing(): Boolean = isShowing

    // ==================== Private Methods ====================

    private fun scheduleAutoHide(message: StatusMessage) {
        if (!autoHideWarnings) return
        
        // Don't auto-hide errors or messages with actions
        if (message.status == AnalysisStatus.ERROR ||
            message.status == AnalysisStatus.BAD_CALIBRATION ||
            message.actionResId != 0) {
            return
        }
        
        handler?.postDelayed(hideRunnable, warningDisplayDuration)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler?.removeCallbacks(hideRunnable)
    }

    // ==================== Constants ====================

    companion object {
        /** Default duration to show warnings (3 seconds) */
        const val DEFAULT_WARNING_DURATION = 3000L
        
        /** Default fade animation duration */
        const val DEFAULT_FADE_DURATION = 200L
    }
}
