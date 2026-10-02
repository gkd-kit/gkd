package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleGroupCountsTest {
    private val policy = RuleGroupPolicy()

    @Test
    fun globalCountsRespectAppOverridesAndCountGroupsRatherThanChildren() {
        val sub = RawSubscription.parse(
            """{
          id:1,name:'Counts',version:1,globalGroups:[
            {key:1,name:'Common',rules:[{matches:'*'},{matches:'*'}]},
            {key:2,name:'Disabled',rules:[{matches:'*'}]},
            {key:3,name:'Restricted',apps:[{id:'app.one',enable:false}],rules:[{matches:'*'}]}
          ]
        }"""
        )
        val apps = listOf("app.one", "app.two", "app.system", "app.launcher").associateWith {
            AppInfo(it, it, 10, "1.0", it == "app.system", 1, false, 0)
        }
        val summary = RuleGroupSummaryBuilder.build(
            policy,
            listOf(UsedSubsEntry(SubsItem(id = 1, enable = true, order = 0), sub)),
            apps,
            emptyList(),
            listOf(
                SubsGlobalGroupConfig(1, 1, exclude = "app.one\n!app.system\napp.two/Page"),
                SubsGlobalGroupConfig(1, 2, enable = false),
                SubsGlobalGroupConfig(1, 3, exclude = "!app.one"),
            ),
            emptyList(),
            launcherAppId = "app.launcher",
        )
        // Personal includes cannot override built-in exclusions; page exclusions do not disable a group.
        assertEquals(
            mapOf("app.one" to 0, "app.two" to 2, "app.system" to 1, "app.launcher" to 0),
            summary.appIdToGlobalGroupCount
        )
    }

    @Test
    fun globalCountsRefreshForVersionAndLauncherChanges() {
        val sub = RawSubscription.parse(
            """{
          id:1,name:'Counts',version:1,globalGroups:[
            {key:1,name:'Version',matchAnyApp:false,apps:[{id:'app.one',versionCode:{minimum:20}}],rules:[{matches:'*'}]},
            {key:2,name:'Default',rules:[{matches:'*'}]}
          ]
        }"""
        )

        fun count(version: Int, launcher: String): Int = RuleGroupSummaryBuilder.build(

            policy,
            listOf(UsedSubsEntry(SubsItem(id = 1, enable = true, order = 0), sub)),
            mapOf(
                "app.one" to AppInfo(
                    "app.one",
                    "App",
                    version,
                    "1.0",
                    false,
                    version.toLong(),
                    false,
                    0
                )
            ),
            emptyList(), emptyList(), emptyList(), launcherAppId = launcher,
        ).appIdToGlobalGroupCount.getValue("app.one")
        assertEquals(1, count(10, "other.launcher"))
        assertEquals(2, count(20, "other.launcher"))
        assertEquals(1, count(20, "app.one"))
    }

}
