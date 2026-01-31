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
        
        // Display recommendations
        if (summary.recommendations.isEmpty()) {
            showEmptyState()
        } else {
            showRecommendations(summary)
        }
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
