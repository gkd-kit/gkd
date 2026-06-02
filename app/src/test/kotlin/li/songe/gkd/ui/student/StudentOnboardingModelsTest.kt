package li.songe.gkd.ui.student

import li.songe.gkd.data.AppConfig
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentOnboardingModelsTest {

    @Test
    fun buildStudentCandidatesOnlyShowsInstalledAppsWithGroupsAndDefaultsOff() {
        val subscription = subscription(
            apps = listOf(
                rawApp(id = "com.installed", name = "订阅已安装", groupCount = 2),
                rawApp(id = "com.no.groups", name = "无规则", groupCount = 0),
                rawApp(id = "com.not.installed", name = "未安装", groupCount = 1),
            )
        )

        val candidates = buildStudentCandidates(
            subscription = subscription,
            installedApps = mapOf(
                "com.installed" to appInfo(id = "com.installed", name = "Installed App"),
                "com.no.groups" to appInfo(id = "com.no.groups", name = "No Groups App"),
            ),
            selectedAppIds = emptySet(),
            query = "",
        )

        assertEquals(
            listOf(
                StudentAppCandidate(
                    appId = "com.installed",
                    appName = "Installed App",
                    subscriptionName = "订阅已安装",
                    groupCount = 2,
                    selected = false,
                )
            ),
            candidates,
        )
    }

    @Test
    fun buildStudentCandidatesFiltersByAppNamePackageIdAndSubscriptionName() {
        val subscription = subscription(
            apps = listOf(
                rawApp(id = "com.study.reader", name = "阅读订阅", groupCount = 1),
                rawApp(id = "org.notes.client", name = "笔记规则", groupCount = 1),
            )
        )
        val installedApps = mapOf(
            "com.study.reader" to appInfo(id = "com.study.reader", name = "Study Reader"),
            "org.notes.client" to appInfo(id = "org.notes.client", name = "Notes Client"),
        )

        assertEquals(
            listOf("com.study.reader"),
            buildStudentCandidates(
                subscription = subscription,
                installedApps = installedApps,
                selectedAppIds = emptySet(),
                query = "study",
            ).map { it.appId },
        )
        assertEquals(
            listOf("org.notes.client"),
            buildStudentCandidates(
                subscription = subscription,
                installedApps = installedApps,
                selectedAppIds = emptySet(),
                query = "ORG.NOTES",
            ).map { it.appId },
        )
        assertEquals(
            listOf("org.notes.client"),
            buildStudentCandidates(
                subscription = subscription,
                installedApps = installedApps,
                selectedAppIds = emptySet(),
                query = "笔记",
            ).map { it.appId },
        )
    }

    @Test
    fun buildStudentAppConfigsWritesExplicitEntriesForEverySupportedSubscriptionApp() {
        val subsId = 233L
        val subscription = subscription(
            apps = listOf(
                rawApp(id = "com.selected", name = "已选", groupCount = 1),
                rawApp(id = "com.unselected", name = "未选", groupCount = 2),
                rawApp(id = "com.empty", name = "空规则", groupCount = 0),
                rawApp(id = "com.new.selected", name = "新增已选", groupCount = 1),
            )
        )

        val configs = buildStudentAppConfigs(
            subsId = subsId,
            subscription = subscription,
            selectedAppIds = setOf("com.selected", "com.new.selected"),
            existingConfigs = listOf(
                AppConfig(id = 11L, enable = false, subsId = subsId, appId = "com.selected"),
                AppConfig(id = 22L, enable = true, subsId = subsId, appId = "com.unselected"),
                AppConfig(id = 33L, enable = false, subsId = 999L, appId = "com.new.selected"),
            ),
        )

        val byAppId = configs.associateBy { it.appId }
        assertEquals(setOf("com.selected", "com.unselected", "com.new.selected"), byAppId.keys)
        assertEquals(11L, byAppId.getValue("com.selected").id)
        assertTrue(byAppId.getValue("com.selected").enable)
        assertEquals(22L, byAppId.getValue("com.unselected").id)
        assertFalse(byAppId.getValue("com.unselected").enable)
        assertTrue(byAppId.getValue("com.new.selected").enable)
        assertEquals(subsId, byAppId.getValue("com.new.selected").subsId)
        assertNotEquals(33L, byAppId.getValue("com.new.selected").id)
    }

    @Test
    fun buildStudentGlobalDisableConfigsWritesDisableEntriesForEachGlobalGroup() {
        val subsId = 233L
        val configs = buildStudentGlobalDisableConfigs(
            subsId = subsId,
            subscription = subscription(
                globalGroups = listOf(
                    globalGroup(key = 1, name = "全局一"),
                    globalGroup(key = 2, name = "全局二"),
                )
            ),
            existingConfigs = listOf(
                SubsConfig(
                    id = 101L,
                    type = SubsConfig.GlobalGroupType,
                    enable = true,
                    subsId = subsId,
                    groupKey = 1,
                ),
                SubsConfig(
                    id = 202L,
                    type = SubsConfig.AppGroupType,
                    enable = true,
                    subsId = subsId,
                    appId = "com.app",
                    groupKey = 2,
                ),
                SubsConfig(
                    id = 303L,
                    type = SubsConfig.GlobalGroupType,
                    enable = true,
                    subsId = 999L,
                    groupKey = 2,
                ),
            ),
        )

        val byGroupKey = configs.associateBy { it.groupKey }
        assertEquals(setOf(1, 2), byGroupKey.keys)
        assertEquals(101L, byGroupKey.getValue(1).id)
        byGroupKey.values.forEach { config ->
            assertEquals(SubsConfig.GlobalGroupType, config.type)
            assertEquals(false, config.enable)
            assertEquals(subsId, config.subsId)
            assertEquals("", config.appId)
            assertEquals("", config.exclude)
        }
        assertNotEquals(303L, byGroupKey.getValue(2).id)
    }

    private fun subscription(
        apps: List<RawSubscription.RawApp> = emptyList(),
        globalGroups: List<RawSubscription.RawGlobalGroup> = emptyList(),
    ) = RawSubscription(
        id = 1L,
        name = "测试订阅",
        version = 1,
        apps = apps,
        globalGroups = globalGroups,
    )

    private fun rawApp(
        id: String,
        name: String,
        groupCount: Int,
    ) = RawSubscription.RawApp(
        id = id,
        name = name,
        groups = (1..groupCount).map { key -> appGroup(key = key, name = "$name-$key") },
    )

    private fun appGroup(
        key: Int,
        name: String,
    ) = RawSubscription.RawAppGroup(
        key = key,
        name = name,
        desc = null,
        enable = null,
        scopeKeys = null,
        actionCdKey = null,
        actionMaximumKey = null,
        actionCd = null,
        actionDelay = null,
        fastQuery = null,
        matchRoot = null,
        actionMaximum = null,
        priorityTime = null,
        priorityActionMaximum = null,
        order = null,
        forcedTime = null,
        matchDelay = null,
        matchTime = null,
        resetMatch = null,
        snapshotUrls = null,
        excludeSnapshotUrls = null,
        exampleUrls = null,
        activityIds = null,
        excludeActivityIds = null,
        rules = emptyList(),
        versionCode = null,
        versionName = null,
        ignoreGlobalGroupMatch = null,
    )

    private fun globalGroup(
        key: Int,
        name: String,
    ) = RawSubscription.RawGlobalGroup(
        key = key,
        name = name,
        desc = null,
        enable = null,
        scopeKeys = null,
        actionCd = null,
        actionDelay = null,
        fastQuery = null,
        matchRoot = null,
        matchDelay = null,
        matchTime = null,
        actionMaximum = null,
        resetMatch = null,
        actionCdKey = null,
        actionMaximumKey = null,
        priorityTime = null,
        priorityActionMaximum = null,
        order = null,
        forcedTime = null,
        snapshotUrls = null,
        excludeSnapshotUrls = null,
        exampleUrls = null,
        matchAnyApp = null,
        matchSystemApp = null,
        matchLauncher = null,
        disableIfAppGroupMatch = null,
        rules = emptyList(),
        apps = null,
    )

    private fun appInfo(
        id: String,
        name: String,
    ) = AppInfo(
        id = id,
        name = name,
        versionCode = 1,
        versionName = "1.0",
        isSystem = false,
        mtime = 1L,
        hidden = false,
        enabled = true,
        userId = 0,
    )
}
