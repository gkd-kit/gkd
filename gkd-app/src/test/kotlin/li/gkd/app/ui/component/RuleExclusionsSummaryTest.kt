package li.gkd.app.ui.component

import li.gkd.app.data.ExcludeData
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleExclusionsSummaryTest {
    private val exclude = ExcludeData(
        appIds = mapOf("app.one" to true, "app.two" to false),
        activityIds = setOf("app.one" to "PageA", "app.two" to "PageB"),
    )

    @Test
    fun globalEditorSummaryIncludesBothAppOverridesAndAllPages() {
        assertEquals("2 apps · 2 pages", RulePropertyText.personalSummary(exclude, null))
        assertEquals("2 apps", RulePropertyText.personalSummary(exclude.copy(activityIds = emptySet()), null))
    }

    @Test
    fun pageEditorSummaryCountsOnlyPagesInItsApp() {
        assertEquals("1 page", RulePropertyText.personalSummary(exclude, "app.one"))
        assertEquals(null, RulePropertyText.personalSummary(exclude, "app.three"))
    }
}
