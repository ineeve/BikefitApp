package pt.ineeve.bikefitapp.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import pt.ineeve.bikefitapp.R

/**
 * Represents a guidance tip to display to the user.
 * 
 * @param textResId String resource ID for the tip text
 * @param iconResId Optional drawable resource ID for an icon
 */
data class GuidanceTip(
    val textResId: Int,
    val iconResId: Int = 0
)

/**
 * A view that displays recording guidance tips to help users capture usable footage.
 * 
 * Features:
 * - Displays helpful tips one at a time with smooth transitions
 * - Auto-advances through tips on a timer
 * - User can dismiss the entire overlay
 * - Non-intrusive overlay at bottom of screen
 * - Fades out automatically after all tips are shown
 * 
 * Usage:
 * ```xml
 * <pt.ineeve.bikefitapp.ui.RecordingGuidanceView
 *     android:id="@+id/recording_guidance"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 * 
 * ```kotlin
 * val guidance = findViewById<RecordingGuidanceView>(R.id.recording_guidance)
 * guidance.startGuidance()
 * ```
 */
class RecordingGuidanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ==================== Configuration ====================

    /** Duration each tip is displayed in milliseconds */
    var tipDisplayDuration: Long = DEFAULT_TIP_DISPLAY_DURATION
    
    /** Duration for fade animations in milliseconds */
    var fadeAnimationDuration: Long = DEFAULT_FADE_DURATION
    
    /** Whether to auto-dismiss after showing all tips */
    var autoDismissAfterComplete: Boolean = true
    
    /** Delay before auto-dismiss after last tip */
    var autoDismissDelay: Long = DEFAULT_AUTO_DISMISS_DELAY

    // ==================== Views ====================

    private val containerLayout: LinearLayout
    private val tipTextView: TextView
    private val dismissButton: ImageButton
    private val tipIndicator: TextView

    // ==================== State ====================

    private val handler = Handler(Looper.getMainLooper())
    private var currentTipIndex = 0
    private var isAnimating = false
    private var isDismissed = false
    
    private var tips: List<GuidanceTip> = getDefaultTips()
    
    /** Callback when guidance is dismissed */
    var onDismissListener: (() -> Unit)? = null
    
    /** Callback when all tips have been shown */
    var onCompleteListener: (() -> Unit)? = null

    // ==================== Initialization ====================

    init {
        // Inflate the layout
        LayoutInflater.from(context).inflate(R.layout.view_recording_guidance, this, true)
        
        // Find views
        containerLayout = findViewById(R.id.guidance_container)
        tipTextView = findViewById(R.id.guidance_tip_text)
        dismissButton = findViewById(R.id.guidance_dismiss_button)
        tipIndicator = findViewById(R.id.guidance_tip_indicator)
        
        // Setup dismiss button
        dismissButton.setOnClickListener {
            dismiss()
        }
        
        // Start hidden
        alpha = 0f
        visibility = View.GONE
    }

    // ==================== Public API ====================

    /**
     * Sets custom tips to display.
     * 
     * @param customTips List of guidance tips to show
     */
    fun setTips(customTips: List<GuidanceTip>) {
        tips = customTips.ifEmpty { getDefaultTips() }
        currentTipIndex = 0
    }

    /**
     * Starts displaying the guidance tips.
     * 
     * Tips will auto-advance and the overlay will fade in.
     */
    fun startGuidance() {
        if (tips.isEmpty() || isDismissed) return
        
        currentTipIndex = 0
        isDismissed = false
        visibility = View.VISIBLE
        
        // Show first tip
        updateTipDisplay()
        
        // Fade in
        animate()
            .alpha(1f)
            .setDuration(fadeAnimationDuration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    scheduleNextTip()
                }
            })
            .start()
    }

    /**
     * Dismisses the guidance overlay with a fade animation.
     */
    fun dismiss() {
        if (isDismissed) return
        
        isDismissed = true
        handler.removeCallbacksAndMessages(null)
        
        animate()
            .alpha(0f)
            .setDuration(fadeAnimationDuration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    onDismissListener?.invoke()
                }
            })
            .start()
    }

    /**
     * Resets the guidance to initial state.
     */
    fun reset() {
        handler.removeCallbacksAndMessages(null)
        currentTipIndex = 0
        isDismissed = false
        alpha = 0f
        visibility = View.GONE
    }

    /**
     * Returns true if the guidance is currently visible.
     */
    fun isShowing(): Boolean = visibility == View.VISIBLE && !isDismissed

    /**
     * Returns the current tip index (0-based).
     */
    fun getCurrentTipIndex(): Int = currentTipIndex

    /**
     * Returns the total number of tips.
     */
    fun getTipCount(): Int = tips.size

    // ==================== Private Methods ====================

    private fun updateTipDisplay() {
        if (currentTipIndex >= tips.size) return
        
        val tip = tips[currentTipIndex]
        tipTextView.setText(tip.textResId)
        tipIndicator.text = context.getString(
            R.string.guidance_tip_indicator,
            currentTipIndex + 1,
            tips.size
        )
    }

    private fun scheduleNextTip() {
        if (isDismissed) return
        
        handler.postDelayed({
            advanceToNextTip()
        }, tipDisplayDuration)
    }

    private fun advanceToNextTip() {
        if (isDismissed || isAnimating) return
        
        currentTipIndex++
        
        if (currentTipIndex >= tips.size) {
            // All tips shown
            onCompleteListener?.invoke()
            
            if (autoDismissAfterComplete) {
                handler.postDelayed({
                    dismiss()
                }, autoDismissDelay)
            }
            return
        }
        
        // Animate transition to next tip
        animateTipTransition()
    }

    private fun animateTipTransition() {
        isAnimating = true
        
        // Fade out current text
        val fadeOut = ObjectAnimator.ofFloat(tipTextView, "alpha", 1f, 0f)
        fadeOut.duration = fadeAnimationDuration / 2
        
        // Fade in new text
        val fadeIn = ObjectAnimator.ofFloat(tipTextView, "alpha", 0f, 1f)
        fadeIn.duration = fadeAnimationDuration / 2
        
        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(fadeOut, fadeIn)
        
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                updateTipDisplay()
            }
        })
        
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                scheduleNextTip()
            }
        })
        
        animatorSet.start()
    }

    private fun getDefaultTips(): List<GuidanceTip> {
        return listOf(
            GuidanceTip(R.string.guidance_tip_side_view),
            GuidanceTip(R.string.guidance_tip_hip_height),
            GuidanceTip(R.string.guidance_tip_full_bike),
            GuidanceTip(R.string.guidance_tip_start_pedaling)
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    // ==================== Constants ====================

    companion object {
        /** Default duration for each tip in milliseconds */
        const val DEFAULT_TIP_DISPLAY_DURATION = 4000L
        
        /** Default fade animation duration */
        const val DEFAULT_FADE_DURATION = 300L
        
        /** Default delay before auto-dismiss */
        const val DEFAULT_AUTO_DISMISS_DELAY = 1500L
        
        /**
         * Returns the default guidance tips.
         * Useful for testing or displaying tips elsewhere.
         */
        fun getDefaultTipResourceIds(): List<Int> {
            return listOf(
                R.string.guidance_tip_side_view,
                R.string.guidance_tip_hip_height,
                R.string.guidance_tip_full_bike,
                R.string.guidance_tip_start_pedaling
            )
        }
    }
}
