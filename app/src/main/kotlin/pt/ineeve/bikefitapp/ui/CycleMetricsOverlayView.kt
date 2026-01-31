package pt.ineeve.bikefitapp.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import pt.ineeve.bikefitapp.R
import java.util.Locale

/**
 * Overlay view to display real-time cycle metrics.
 * 
 * Shows:
 * - Current Knee Angle
 * - Max Extension (from last complete cycle)
 * - Min Flexion (from last complete cycle)
 */
class CycleMetricsOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val kneeAngleText: TextView
    private val maxExtensionText: TextView
    private val minFlexionText: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_cycle_metrics_overlay, this, true)
        
        kneeAngleText = findViewById(R.id.metric_knee_angle)
        maxExtensionText = findViewById(R.id.metric_max_extension)
        minFlexionText = findViewById(R.id.metric_min_flexion)
        
        // Initial state
        reset()
    }
    
    /**
     * Updates the real-time knee angle.
     */
    fun updateCurrentKneeAngle(angle: Float) {
        kneeAngleText.text = String.format(Locale.getDefault(), "%.0f°", angle)
    }
    
    /**
     * Updates the cycle metrics from a completed cycle.
     */
    fun updateCycleMetrics(maxExtension: Float, minFlexion: Float) {
        maxExtensionText.text = String.format(Locale.getDefault(), "%.0f°", maxExtension)
        minFlexionText.text = String.format(Locale.getDefault(), "%.0f°", minFlexion)
    }
    
    /**
     * Resets the display to default values.
     */
    fun reset() {
        kneeAngleText.text = "--"
        maxExtensionText.text = "--"
        minFlexionText.text = "--"
    }
}
