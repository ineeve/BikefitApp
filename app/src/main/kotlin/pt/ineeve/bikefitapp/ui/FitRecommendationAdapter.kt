package pt.ineeve.bikefitapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ineeve.bikefitapp.R
import pt.ineeve.bikefitapp.fit.FitRecommendation
import pt.ineeve.bikefitapp.fit.Severity

/**
 * RecyclerView adapter for displaying fit recommendations.
 * 
 * Displays each recommendation as a card with:
 * - Severity indicator (colored bar)
 * - Priority badge (numbered)
 * - Category label
 * - Title and description
 * - Actionable recommendation
 * 
 * Uses ListAdapter with DiffUtil for efficient updates.
 */
class FitRecommendationAdapter(
    private val onItemClick: ((FitRecommendation) -> Unit)? = null
) : ListAdapter<FitRecommendation, FitRecommendationAdapter.RecommendationViewHolder>(
    RecommendationDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fit_recommendation, parent, false)
        return RecommendationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    /**
     * ViewHolder for a single recommendation item.
     */
    class RecommendationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val severityIndicator: View = itemView.findViewById(R.id.severity_indicator)
        private val priorityBadge: TextView = itemView.findViewById(R.id.priority_badge)
        private val categoryLabel: TextView = itemView.findViewById(R.id.category_label)
        private val severityIcon: ImageView = itemView.findViewById(R.id.severity_icon)
        private val title: TextView = itemView.findViewById(R.id.recommendation_title)
        private val description: TextView = itemView.findViewById(R.id.recommendation_description)
        private val action: TextView = itemView.findViewById(R.id.recommendation_action)

        fun bind(recommendation: FitRecommendation, onItemClick: ((FitRecommendation) -> Unit)?) {
            val context = itemView.context

            // Set severity indicator color
            val severityColor = getSeverityColor(recommendation.severity)
            severityIndicator.setBackgroundColor(ContextCompat.getColor(context, severityColor))

            // Set priority badge
            priorityBadge.text = recommendation.priority.toString()
            priorityBadge.background.setTint(ContextCompat.getColor(context, severityColor))

            // Set category label
            categoryLabel.text = recommendation.category.displayName()

            // Set severity icon
            val severityIconRes = getSeverityIcon(recommendation.severity)
            severityIcon.setImageResource(severityIconRes)
            severityIcon.setColorFilter(ContextCompat.getColor(context, severityColor))

            // Set text content
            title.text = recommendation.title
            description.text = recommendation.description
            action.text = "➡ ${recommendation.action}"

            // Set click listener
            onItemClick?.let { listener ->
                itemView.setOnClickListener { listener(recommendation) }
            }
        }

        /**
         * Returns the color resource for a severity level.
         */
        private fun getSeverityColor(severity: Severity): Int {
            return when (severity) {
                Severity.HIGH -> R.color.severity_high
                Severity.MEDIUM -> R.color.severity_medium
                Severity.LOW -> R.color.severity_low
            }
        }

        /**
         * Returns the icon resource for a severity level.
         */
        private fun getSeverityIcon(severity: Severity): Int {
            return when (severity) {
                Severity.HIGH -> android.R.drawable.ic_dialog_alert
                Severity.MEDIUM -> android.R.drawable.ic_dialog_info
                Severity.LOW -> android.R.drawable.ic_menu_info_details
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates.
     */
    class RecommendationDiffCallback : DiffUtil.ItemCallback<FitRecommendation>() {
        override fun areItemsTheSame(
            oldItem: FitRecommendation,
            newItem: FitRecommendation
        ): Boolean {
            return oldItem.priority == newItem.priority && 
                   oldItem.category == newItem.category
        }

        override fun areContentsTheSame(
            oldItem: FitRecommendation,
            newItem: FitRecommendation
        ): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        /**
         * Returns the color resource for a severity level.
         * Useful for external components that need severity colors.
         */
        fun getSeverityColorRes(severity: Severity): Int {
            return when (severity) {
                Severity.HIGH -> R.color.severity_high
                Severity.MEDIUM -> R.color.severity_medium
                Severity.LOW -> R.color.severity_low
            }
        }
    }
}
