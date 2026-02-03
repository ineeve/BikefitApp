package pt.ineeve.bikefitapp.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import pt.ineeve.bikefitapp.R
import pt.ineeve.bikefitapp.fit.FitGrade
import pt.ineeve.bikefitapp.fit.FitSummary

/**
 * Activity that displays the bike fit analysis summary and recommendations.
 * 
 * Shows:
 * - Overall fit grade with emoji and color
 * - Brief summary text
 * - Issue count
 * - List of prioritized recommendations with severity indicators
 * 
 * Usage:
 * ```
 * // Start with a FitSummary
 * val intent = FitSummaryActivity.createIntent(context)
 * FitSummaryActivity.currentSummary = summary
 * startActivity(intent)
 * ```
 */
class FitSummaryActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var gradeCard: MaterialCardView
    private lateinit var gradeEmoji: TextView
    private lateinit var gradeText: TextView
    private lateinit var gradeSummary: TextView
    private lateinit var issueCount: TextView
    private lateinit var recommendationsHeader: TextView
    private lateinit var recommendationsRecycler: RecyclerView
    private lateinit var emptyState: View

    // Metrics table views for the 4 key bike fit metrics:
    // A. Knee Flexion/Extension at BDC
    // B. Hip Angle at TDC
    // C. Torso Angle
    // D. Ankle Angle at BDC
    private lateinit var metricsCard: MaterialCardView
    private lateinit var metricKneeBdcValue: TextView
    private lateinit var metricKneeBdcRange: TextView
    private lateinit var metricKneeBdcStats: TextView
    private lateinit var metricHipTdcValue: TextView
    private lateinit var metricHipTdcRange: TextView
    private lateinit var metricHipTdcStats: TextView
    private lateinit var metricTorsoValue: TextView
    private lateinit var metricTorsoRange: TextView
    private lateinit var metricTorsoStats: TextView
    private lateinit var metricAnkleBdcValue: TextView
    private lateinit var metricAnkleBdcRange: TextView
    private lateinit var metricAnkleBdcStats: TextView
    // E. Knee Over Pedal (Normalized)
    private lateinit var metricKopsValue: TextView
    private lateinit var metricKopsRange: TextView
    private lateinit var metricKopsStats: TextView
    
    // Additional stats views
    private lateinit var cadenceValue: TextView
    private lateinit var cycleCountValue: TextView
    private lateinit var dataQualityValue: TextView

    private lateinit var adapter: FitRecommendationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fit_summary)

        initViews()
        setupToolbar()
        setupRecyclerView()
        
        // Load the summary
        val summary = currentSummary ?: FitSummary.optimal()
        displaySummary(summary)
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        gradeCard = findViewById(R.id.grade_card)
        gradeEmoji = findViewById(R.id.grade_emoji)
        gradeText = findViewById(R.id.grade_text)
        gradeSummary = findViewById(R.id.grade_summary)
        issueCount = findViewById(R.id.issue_count)
        
        // Metrics Views - 4 key bike fit metrics
        metricsCard = findViewById(R.id.metrics_card)
        // A. Knee Flexion/Extension at BDC
        metricKneeBdcValue = findViewById(R.id.metric_knee_bdc_value)
        metricKneeBdcRange = findViewById(R.id.metric_knee_bdc_range)
        metricKneeBdcStats = findViewById(R.id.metric_knee_bdc_stats)
        // B. Hip Angle at TDC
        metricHipTdcValue = findViewById(R.id.metric_hip_tdc_value)
        metricHipTdcRange = findViewById(R.id.metric_hip_tdc_range)
        metricHipTdcStats = findViewById(R.id.metric_hip_tdc_stats)
        // C. Torso Angle
        metricTorsoValue = findViewById(R.id.metric_torso_value)
        metricTorsoRange = findViewById(R.id.metric_torso_range)
        metricTorsoStats = findViewById(R.id.metric_torso_stats)
        // D. Ankle Angle at BDC
        metricAnkleBdcValue = findViewById(R.id.metric_ankle_bdc_value)
        metricAnkleBdcRange = findViewById(R.id.metric_ankle_bdc_range)
        metricAnkleBdcStats = findViewById(R.id.metric_ankle_bdc_stats)
        // E. Knee Over Pedal (Normalized)
        metricKopsValue = findViewById(R.id.metric_kops_value)
        metricKopsRange = findViewById(R.id.metric_kops_range)
        metricKopsStats = findViewById(R.id.metric_kops_stats)
        
        // Additional stats
        cadenceValue = findViewById(R.id.cadence_value)
        cycleCountValue = findViewById(R.id.cycle_count_value)
        dataQualityValue = findViewById(R.id.data_quality_value)

        recommendationsHeader = findViewById(R.id.recommendations_header)
        recommendationsRecycler = findViewById(R.id.recommendations_recycler)
        emptyState = findViewById(R.id.empty_state)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = FitRecommendationAdapter { recommendation ->
            // Handle recommendation click if needed
            // For now, no action is taken
        }
        
        recommendationsRecycler.layoutManager = LinearLayoutManager(this)
        recommendationsRecycler.adapter = adapter
    }

    /**
     * Displays the fit summary in the UI.
     */
    private fun displaySummary(summary: FitSummary) {
        // Display grade
        displayGrade(summary.grade)
        
        // Display brief summary
        gradeSummary.text = summary.briefSummary()
        
        // Display issue count
        displayIssueCount(summary)
        
        // Display metrics table
        displayMetrics(summary)
        
        // Display recommendations
        if (summary.recommendations.isEmpty()) {
            showEmptyState()
        } else {
            showRecommendations(summary)
        }
    }

    private fun displayMetrics(summary: FitSummary) {
        val metrics = summary.cycleSummary
        
        if (metrics == null) {
            metricsCard.visibility = View.GONE
            return
        }
        
        // A. Knee Flexion/Extension at BDC (hip → knee → ankle)
        val kneeBdcStats = metrics.kneeAngleAtBdcStats
        if (kneeBdcStats.isValid) {
            metricKneeBdcValue.text = "%.1f°".format(metrics.averageKneeAngleAtBdc ?: 0f)
            metricKneeBdcRange.text = "145° - 155°"
            metricKneeBdcStats.text = "Min: %.1f° | Max: %.1f° | SD: %.1f°".format(
                kneeBdcStats.min, kneeBdcStats.max, kneeBdcStats.standardDeviation
            )
            metricKneeBdcStats.visibility = View.VISIBLE
        } else {
            metricKneeBdcValue.text = "--"
            metricKneeBdcRange.text = "145° - 155°"
            metricKneeBdcStats.visibility = View.GONE
        }
        
        // B. Hip Angle at TDC (shoulder/torso → hip → knee) - minimum hip angle
        val hipTdcStats = metrics.hipAngleAtTdcStats
        if (hipTdcStats.isValid) {
            metricHipTdcValue.text = "%.1f°".format(metrics.averageHipAngleAtTdc ?: 0f)
            metricHipTdcRange.text = "40° - 70°"
            metricHipTdcStats.text = "Min: %.1f° | Max: %.1f° | SD: %.1f°".format(
                hipTdcStats.min, hipTdcStats.max, hipTdcStats.standardDeviation
            )
            metricHipTdcStats.visibility = View.VISIBLE
        } else {
            metricHipTdcValue.text = "--"
            metricHipTdcRange.text = "40° - 70°"
            metricHipTdcStats.visibility = View.GONE
        }
        
        // C. Torso Angle (shoulder → hip relative to horizontal)
        val torsoStats = metrics.torsoAngleStats
        if (torsoStats.isValid) {
            metricTorsoValue.text = "%.1f°".format(metrics.averageTorsoAngle)
            metricTorsoRange.text = "30° - 60°"
            metricTorsoStats.text = "Min: %.1f° | Max: %.1f° | SD: %.1f°".format(
                torsoStats.min, torsoStats.max, torsoStats.standardDeviation
            )
            metricTorsoStats.visibility = View.VISIBLE
        } else {
            metricTorsoValue.text = "--"
            metricTorsoRange.text = "30° - 60°"
            metricTorsoStats.visibility = View.GONE
        }
        
        // D. Ankle Plantarflexion at BDC (knee → ankle → foot index)
        val ankleBdcStats = metrics.ankleAngleAtBdcStats
        if (ankleBdcStats.isValid) {
            metricAnkleBdcValue.text = "%.1f°".format(metrics.averageAnkleAngleAtBdc ?: 0f)
            metricAnkleBdcRange.text = "20° - 30°"
            metricAnkleBdcStats.text = "Min: %.1f° | Max: %.1f° | SD: %.1f°".format(
                ankleBdcStats.min, ankleBdcStats.max, ankleBdcStats.standardDeviation
            )
            metricAnkleBdcStats.visibility = View.VISIBLE
        } else {
            metricAnkleBdcValue.text = "--"
            metricAnkleBdcRange.text = "20° - 30°"
            metricAnkleBdcStats.visibility = View.GONE
        }
        
        // E. Knee Over Pedal (Normalized) - ratio of knee offset to femur length
        val kopsStats = metrics.kopsNormalizedStats
        if (kopsStats.isValid) {
            metricKopsValue.text = "%.2f".format(metrics.averageKopsNormalized ?: 0f)
            metricKopsRange.text = "-0.1 - 0.1"
            metricKopsStats.text = "Min: %.2f | Max: %.2f | SD: %.2f".format(
                kopsStats.min, kopsStats.max, kopsStats.standardDeviation
            )
            metricKopsStats.visibility = View.VISIBLE
        } else {
            metricKopsValue.text = "--"
            metricKopsRange.text = "-0.1 - 0.1"
            metricKopsStats.visibility = View.GONE
        }
        
        // Cadence
        metrics.averageCadenceRpm?.let { cadence ->
            cadenceValue.text = "%.0f RPM".format(cadence)
        } ?: run {
            cadenceValue.text = "--"
        }
        
        // Cycle Count - show filtered/total if outliers were removed
        if (metrics.outlierCount > 0) {
            val totalCycles = metrics.cycleCount + metrics.outlierCount
            cycleCountValue.text = "%d of %d".format(metrics.cycleCount, totalCycles)
        } else {
            cycleCountValue.text = "%d".format(metrics.cycleCount)
        }
        
        // Data Quality
        val quality = metrics.dataQuality
        val qualityPercent = (quality * 100).toInt()
        dataQualityValue.text = "%d%%".format(qualityPercent)
        dataQualityValue.setTextColor(ContextCompat.getColor(this, when {
            quality >= 0.8f -> R.color.grade_excellent
            quality >= 0.6f -> R.color.grade_good
            quality >= 0.4f -> R.color.grade_fair
            else -> R.color.grade_poor
        }))
        
        metricsCard.visibility = View.VISIBLE
    }

    /**
     * Displays the fit grade with appropriate styling.
     */
    private fun displayGrade(grade: FitGrade) {
        gradeEmoji.text = grade.emoji()
        gradeText.text = grade.displayText()
        
        // Set card background color based on grade
        val colorRes = getGradeColor(grade)
        gradeCard.setCardBackgroundColor(ContextCompat.getColor(this, colorRes))
        
        // Adjust text color for dark backgrounds
        when (grade) {
            FitGrade.POOR, FitGrade.FAIR -> {
                gradeText.setTextColor(ContextCompat.getColor(this, R.color.white))
                gradeSummary.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            else -> {
                gradeText.setTextColor(ContextCompat.getColor(this, R.color.recommendation_text_primary))
                gradeSummary.setTextColor(ContextCompat.getColor(this, R.color.recommendation_text_secondary))
            }
        }
    }

    /**
     * Displays the issue count badge.
     */
    private fun displayIssueCount(summary: FitSummary) {
        if (summary.totalIssueCount == 0) {
            issueCount.visibility = View.GONE
        } else {
            issueCount.visibility = View.VISIBLE
            issueCount.text = if (summary.highSeverityCount > 0) {
                getString(
                    R.string.issue_count_with_high_format,
                    summary.totalIssueCount,
                    summary.highSeverityCount
                )
            } else {
                getString(R.string.issue_count_format, summary.totalIssueCount)
            }
        }
    }

    /**
     * Shows the recommendations list.
     */
    private fun showRecommendations(summary: FitSummary) {
        emptyState.visibility = View.GONE
        recommendationsHeader.visibility = View.VISIBLE
        recommendationsRecycler.visibility = View.VISIBLE
        
        adapter.submitList(summary.recommendations)
    }

    /**
     * Shows the empty state for optimal fit.
     */
    private fun showEmptyState() {
        recommendationsHeader.visibility = View.GONE
        recommendationsRecycler.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
    }

    /**
     * Returns the color resource for a fit grade.
     */
    private fun getGradeColor(grade: FitGrade): Int {
        return when (grade) {
            FitGrade.EXCELLENT -> R.color.grade_excellent
            FitGrade.GOOD -> R.color.grade_good
            FitGrade.FAIR -> R.color.grade_fair
            FitGrade.POOR -> R.color.grade_poor
        }
    }

    companion object {
        /**
         * Current summary to display.
         * 
         * Set this before starting the activity. This is a simple approach
         * for passing complex objects without Parcelable/Serializable.
         * For production, consider using a ViewModel or Parcelable.
         */
        var currentSummary: FitSummary? = null

        /**
         * Creates an intent to start this activity.
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, FitSummaryActivity::class.java)
        }

        /**
         * Starts the activity with the given summary.
         */
        fun start(context: Context, summary: FitSummary) {
            currentSummary = summary
            context.startActivity(createIntent(context))
        }
    }
}
