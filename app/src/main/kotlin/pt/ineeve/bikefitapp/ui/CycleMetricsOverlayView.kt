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
 * - Current Knee Angle (real-time)
 * - Current Hip Angle (real-time)
 * - Current Torso Angle (real-time)
 * - Max Extension (from last complete cycle)
 * - Min Flexion (from last complete cycle)
 */
class CycleMetricsOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val kneeAngleText: TextView
    private val hipAngleText: TextView
    private val torsoAngleText: TextView
    private val cycleCountText: TextView
    private val maxExtensionText: TextView
    private val minFlexionText: TextView
    private val cadenceText: TextView
    private val crankAngleText: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_cycle_metrics_overlay, this, true)
        
        kneeAngleText = findViewById(R.id.metric_knee_angle)
        hipAngleText = findViewById(R.id.metric_hip_angle)
        torsoAngleText = findViewById(R.id.metric_torso_angle)
        cycleCountText = findViewById(R.id.metric_cycles)
        maxExtensionText = findViewById(R.id.metric_max_extension)
        minFlexionText = findViewById(R.id.metric_min_flexion)
        cadenceText = findViewById(R.id.metric_cadence)
        crankAngleText = findViewById(R.id.metric_crank_angle)
        
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
     * Updates the real-time hip angle.
     */
    fun updateCurrentHipAngle(angle: Float) {
        hipAngleText.text = String.format(Locale.getDefault(), "%.0f°", angle)
    }
    
    /**
     * Updates the real-time torso angle.
     */
    fun updateCurrentTorsoAngle(angle: Float) {
        torsoAngleText.text = String.format(Locale.getDefault(), "%.0f°", angle)
    }
    
    /**
     * Updates the cycle count.
     */
    fun updateCycleCount(count: Int) {
        cycleCountText.text = count.toString()
    }
    
    /**
     * Updates the cycle metrics from a completed cycle.
     */
    fun updateCycleMetrics(maxExtension: Float, minFlexion: Float) {
        maxExtensionText.text = String.format(Locale.getDefault(), "%.0f°", maxExtension)
        minFlexionText.text = String.format(Locale.getDefault(), "%.0f°", minFlexion)
    }
    
    /**
     * Updates the current cadence.
     */
    fun updateCurrentCadence(cadence: Float) {
        cadenceText.text = String.format(Locale.getDefault(), "%.0f RPM", cadence)
    }
    
    /**
     * Updates the current crank angle.
     */
    fun updateCurrentCrankAngle(angle: Float) {
        crankAngleText.text = String.format(Locale.getDefault(), "%.0f°", angle)
    }
    
    /**
     * Resets the display to default values.
     */
    fun reset() {
        kneeAngleText.text = "--"
        hipAngleText.text = "--"
        torsoAngleText.text = "--"
        cycleCountText.text = "0"
        maxExtensionText.text = "--"
        minFlexionText.text = "--"
        cadenceText.text = "0"
        crankAngleText.text = "--"
    }
}
