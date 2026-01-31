package pt.ineeve.bikefitapp.ui

import org.junit.Assert.*
import org.junit.Test
import pt.ineeve.bikefitapp.R
import pt.ineeve.bikefitapp.fit.FitCategory
import pt.ineeve.bikefitapp.fit.FitGrade
import pt.ineeve.bikefitapp.fit.FitIssue
import pt.ineeve.bikefitapp.fit.FitRecommendation
import pt.ineeve.bikefitapp.fit.Severity

class FitRecommendationAdapterTest {

    // ==================== Severity Color Tests ====================

    @Test
    fun `getSeverityColorRes returns red for HIGH severity`() {
        val colorRes = FitRecommendationAdapter.getSeverityColorRes(Severity.HIGH)
        assertEquals(R.color.severity_high, colorRes)
    }

    @Test
    fun `getSeverityColorRes returns yellow for MEDIUM severity`() {
        val colorRes = FitRecommendationAdapter.getSeverityColorRes(Severity.MEDIUM)
        assertEquals(R.color.severity_medium, colorRes)
    }

    @Test
    fun `getSeverityColorRes returns green for LOW severity`() {
        val colorRes = FitRecommendationAdapter.getSeverityColorRes(Severity.LOW)
        assertEquals(R.color.severity_low, colorRes)
    }

    @Test
    fun `all severity levels have distinct colors`() {
        val colors = Severity.values().map { FitRecommendationAdapter.getSeverityColorRes(it) }
        assertEquals(colors.size, colors.distinct().size)
    }

    // ==================== DiffUtil Tests ====================

    @Test
    fun `DiffCallback areItemsTheSame returns true for same priority and category`() {
        val diffCallback = FitRecommendationAdapter.RecommendationDiffCallback()
        
        val rec1 = createRecommendation(priority = 1, category = FitCategory.SADDLE)
        val rec2 = createRecommendation(priority = 1, category = FitCategory.SADDLE, title = "Different title")
        
        assertTrue(diffCallback.areItemsTheSame(rec1, rec2))
    }

    @Test
    fun `DiffCallback areItemsTheSame returns false for different priority`() {
        val diffCallback = FitRecommendationAdapter.RecommendationDiffCallback()
        
        val rec1 = createRecommendation(priority = 1, category = FitCategory.SADDLE)
        val rec2 = createRecommendation(priority = 2, category = FitCategory.SADDLE)
        
        assertFalse(diffCallback.areItemsTheSame(rec1, rec2))
    }

    @Test
    fun `DiffCallback areItemsTheSame returns false for different category`() {
        val diffCallback = FitRecommendationAdapter.RecommendationDiffCallback()
        
        val rec1 = createRecommendation(priority = 1, category = FitCategory.SADDLE)
        val rec2 = createRecommendation(priority = 1, category = FitCategory.COCKPIT)
        
        assertFalse(diffCallback.areItemsTheSame(rec1, rec2))
    }

    @Test
    fun `DiffCallback areContentsTheSame returns true for identical recommendations`() {
        val diffCallback = FitRecommendationAdapter.RecommendationDiffCallback()
        
        val rec1 = createRecommendation(priority = 1, category = FitCategory.SADDLE)
        val rec2 = createRecommendation(priority = 1, category = FitCategory.SADDLE)
        
        assertTrue(diffCallback.areContentsTheSame(rec1, rec2))
    }

    @Test
    fun `DiffCallback areContentsTheSame returns false for different content`() {
        val diffCallback = FitRecommendationAdapter.RecommendationDiffCallback()
        
        val rec1 = createRecommendation(priority = 1, title = "Title A")
        val rec2 = createRecommendation(priority = 1, title = "Title B")
        
        assertFalse(diffCallback.areContentsTheSame(rec1, rec2))
    }

    // ==================== Helper Methods ====================

    private fun createRecommendation(
        priority: Int = 1,
        category: FitCategory = FitCategory.SADDLE,
        severity: Severity = Severity.MEDIUM,
        title: String = "Test recommendation",
        description: String = "Test description",
        action: String = "Test action"
    ): FitRecommendation {
        return FitRecommendation(
            priority = priority,
            category = category,
            severity = severity,
            title = title,
            description = description,
            action = action,
            relatedIssues = emptyList()
        )
    }
}

class FitGradeDisplayTest {

    // ==================== Grade Display Tests ====================

    @Test
    fun `EXCELLENT grade has check mark emoji`() {
        assertEquals("✅", FitGrade.EXCELLENT.emoji())
    }

    @Test
    fun `GOOD grade has thumbs up emoji`() {
        assertEquals("👍", FitGrade.GOOD.emoji())
    }

    @Test
    fun `FAIR grade has warning emoji`() {
        assertEquals("⚠️", FitGrade.FAIR.emoji())
    }

    @Test
    fun `POOR grade has red circle emoji`() {
        assertEquals("🔴", FitGrade.POOR.emoji())
    }

    @Test
    fun `all grades have non-empty display text`() {
        for (grade in FitGrade.values()) {
            assertTrue(grade.displayText().isNotEmpty())
        }
    }

    @Test
    fun `all grades have non-empty emoji`() {
        for (grade in FitGrade.values()) {
            assertTrue(grade.emoji().isNotEmpty())
        }
    }

    @Test
    fun `EXCELLENT display text is positive`() {
        assertTrue(FitGrade.EXCELLENT.displayText().lowercase().contains("excellent"))
    }

    @Test
    fun `POOR display text indicates issues`() {
        val text = FitGrade.POOR.displayText().lowercase()
        assertTrue(text.contains("significant") || text.contains("issue") || text.contains("poor"))
    }
}

class FitCategoryDisplayTest {

    // ==================== Category Display Tests ====================

    @Test
    fun `SADDLE category has descriptive display name`() {
        val displayName = FitCategory.SADDLE.displayName()
        assertTrue(displayName.lowercase().contains("saddle"))
    }

    @Test
    fun `COCKPIT category has descriptive display name`() {
        val displayName = FitCategory.COCKPIT.displayName()
        assertTrue(displayName.lowercase().contains("handlebar") || displayName.lowercase().contains("reach"))
    }

    @Test
    fun `all categories have non-empty display names`() {
        for (category in FitCategory.values()) {
            assertTrue(category.displayName().isNotEmpty())
        }
    }

    @Test
    fun `all categories have distinct display names`() {
        val names = FitCategory.values().map { it.displayName() }
        assertEquals(names.size, names.distinct().size)
    }
}
