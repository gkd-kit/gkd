package li.gkd.app.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import li.gkd.app.app.AppCatalogSnapshot
import li.gkd.app.app.AppCatalogState
import li.gkd.app.model.AppInfo
import li.gkd.app.model.AppInventory
import li.gkd.app.state.Loadable
import li.gkd.db.RuleGroupType
import li.gkd.db.RuleTriggerStats

@OptIn(ExperimentalCoroutinesApi::class)
class AppListSourcesTest {
    @Test
    fun activityUpdatesReuseRuleProjectionWhileCatalogAndRuleChangesRefreshIt() = runTest {
        val catalog = MutableStateFlow(AppCatalogState())
        val rules =
            MutableStateFlow<Loadable<Map<String, Int>>>(
                Loadable.Ready(mapOf("app.example" to 2)),
            )
        val actionOrder = MutableStateFlow(emptyList<String>())
        val visits = MutableStateFlow(emptyList<String>())
        val triggerStats = MutableStateFlow(emptyList<RuleTriggerStats>())
        var globalReads = 0
        var groupReads = 0
        val emissions = mutableListOf<Loadable<AppListSource>>()
        backgroundScope.launch {
            AppListSources.observe(
                    catalog,
                    rules,
                    actionOrder,
                    visits,
                    triggerStats,
                    globalGroupCounts = {
                        globalReads++
                        it
                    },
                    appGroups = {
                        groupReads++
                        emptyMap()
                    },
                )
                .collect { emissions += it }
        }
        fun current() = assertIs<Loadable.Ready<AppListSource>>(emissions.last()).value
        fun snapshot(vararg ids: String) =
            AppCatalogSnapshot(
                AppInventory(apps = ids.map { AppInfo(it, it, 1, "1", false, 0, false, 0) }),
            )

        runCurrent()
        assertSame(Loadable.Loading, emissions.last())
        assertEquals(0, globalReads)
        catalog.value = AppCatalogState(snapshot("app.example"))
        runCurrent()
        val initialStats = current().ruleStats
        assertEquals(2, initialStats.getValue("app.example").globalGroups)
        assertEquals(1, globalReads)
        assertEquals(1, groupReads)

        // A trigger, latest-action reorder, or visit must not traverse the rule catalog again.
        triggerStats.value = listOf(RuleTriggerStats(1, RuleGroupType.App, 1, "app.example", 3, 20))
        runCurrent()
        assertEquals(3L, current().triggerStats?.app("app.example")?.count)
        assertSame(initialStats, current().ruleStats)
        actionOrder.value = listOf("app.example")
        runCurrent()
        assertEquals(listOf("app.example"), current().actionOrder)
        assertSame(initialStats, current().ruleStats)
        visits.value = listOf("app.other", "app.example")
        runCurrent()
        assertEquals(mapOf("app.other" to 0, "app.example" to 1), current().visitOrder)
        assertSame(initialStats, current().ruleStats)
        assertEquals(1, globalReads)
        assertEquals(1, groupReads)

        rules.value = Loadable.Ready(mapOf("app.example" to 4, "app.other" to 1))
        runCurrent()
        assertEquals(4, current().ruleStats.getValue("app.example").globalGroups)
        assertEquals(2, globalReads)
        catalog.value = AppCatalogState(snapshot("app.example", "app.other"))
        runCurrent()
        assertEquals(1, current().ruleStats.getValue("app.other").globalGroups)
        assertEquals(3, globalReads)
        assertEquals(3, groupReads)
        assertEquals(3L, current().triggerStats?.app("app.example")?.count)

        // Rule failure remains a partial-data warning, independent of usable app/history data.
        val failure = IllegalStateException("rules unavailable")
        rules.value = Loadable.Failure(failure)
        runCurrent()
        assertSame(failure, current().ruleStatsError)
        assertTrue(current().ruleStats.isEmpty())
        assertEquals(2, current().apps.size)
        assertEquals(listOf("app.example"), current().actionOrder)
        assertEquals(3, globalReads)
        assertEquals(3, groupReads)
    }
}
