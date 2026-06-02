package li.songe.gkd.ui.student

import li.songe.gkd.data.AppConfig
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.store.SettingsStore
import li.songe.gkd.util.AutomatorModeOption
import li.songe.gkd.util.collator

data class StudentAppCandidate(
    val appId: String,
    val appName: String,
    val subscriptionName: String?,
    val groupCount: Int,
    val selected: Boolean,
)

data class StudentRestrictedSettingsHelp(
    val title: String,
    val text: String,
    val primaryActionText: String,
    val secondaryActionText: String,
    val fallbackActionText: String,
) {
    val actionTexts = listOf(primaryActionText, secondaryActionText, fallbackActionText)
}

fun buildStudentRestrictedSettingsHelp() = StudentRestrictedSettingsHelp(
    title = "自动解除系统限制",
    text = "当前系统没有在应用详情页提供稳定的解除开关。先尝试 Shizuku 自动授权；如果没有 Shizuku，就使用命令授权。完成后回到这里继续开启无障碍。",
    primaryActionText = "一键解除限制",
    secondaryActionText = "命令授权",
    fallbackActionText = "查看完整兜底方案",
)

fun buildStudentCompletedStore(store: SettingsStore) = store.copy(
    enableAutomator = true,
    automatorMode = AutomatorModeOption.A11yMode.value,
    enableStatusService = true,
    studentOnboardingCompletedVersion = STUDENT_ONBOARDING_VERSION,
    studentOnboardingCardDismissedVersion = STUDENT_ONBOARDING_VERSION,
)

private val studentAppMatchKeywords = listOf(
    "运动世界校园",
    "慧生活798",
    "慧生活",
    "步道乐跑",
    "闪动校园",
    "智慧校园",
    "志愿汇",
    "学习通",
    "超星",
    "易校园",
    "校园",
    "乐跑",
    "chaoxing",
    "xuexitong",
    "yixiaoyuan",
    "campus",
)

fun buildDefaultStudentSelectedAppIds(
    candidates: List<StudentAppCandidate>,
): Set<String> {
    return candidates.asSequence()
        .filter { candidate ->
            val searchableText = listOf(
                candidate.appId,
                candidate.appName,
                candidate.subscriptionName.orEmpty(),
            ).joinToString(separator = " ").lowercase()
            studentAppMatchKeywords.any { keyword -> searchableText.contains(keyword) }
        }
        .map { candidate -> candidate.appId }
        .toSet()
}

fun isStudentPermissionReady(
    a11yRunning: Boolean,
    canQueryPackages: Boolean,
    appOpsRestricted: Boolean,
): Boolean {
    return a11yRunning && canQueryPackages && !appOpsRestricted
}

private val studentEnabledGlobalGroupKeywords = listOf(
    "开屏广告屏蔽",
    "开屏广告",
)

private fun shouldEnableStudentGlobalGroup(group: RawSubscription.RawGlobalGroup): Boolean {
    return listOf(
        group.name,
        group.desc.orEmpty(),
    ).any { text ->
        studentEnabledGlobalGroupKeywords.any { keyword -> text.contains(keyword) }
    }
}

fun buildStudentCandidates(
    subscription: RawSubscription?,
    installedApps: Map<String, AppInfo>,
    selectedAppIds: Set<String>,
    query: String,
): List<StudentAppCandidate> {
    subscription ?: return emptyList()

    val normalizedQuery = query.trim()
    return subscription.apps.asSequence()
        .filter { rawApp -> rawApp.groups.isNotEmpty() }
        .mapNotNull { rawApp ->
            val installedApp = installedApps[rawApp.id] ?: return@mapNotNull null
            StudentAppCandidate(
                appId = rawApp.id,
                appName = installedApp.name,
                subscriptionName = rawApp.name,
                groupCount = rawApp.groups.size,
                selected = selectedAppIds.contains(rawApp.id),
            )
        }
        .filter { candidate ->
            normalizedQuery.isEmpty() ||
                candidate.appName.contains(normalizedQuery, ignoreCase = true) ||
                candidate.appId.contains(normalizedQuery, ignoreCase = true) ||
                candidate.subscriptionName?.contains(normalizedQuery, ignoreCase = true) == true
        }
        .sortedWith { a, b ->
            collator.compare(a.appName, b.appName).takeIf { it != 0 }
                ?: collator.compare(a.appId, b.appId)
        }
        .toList()
}

fun buildStudentAppConfigs(
    subsId: Long,
    subscription: RawSubscription,
    selectedAppIds: Set<String>,
    existingConfigs: List<AppConfig>,
): List<AppConfig> {
    val usedConfigIds = existingConfigs.mapTo(mutableSetOf()) { config -> config.id }
    var nextGeneratedConfigId = System.currentTimeMillis()
    fun nextConfigId(): Long {
        while (usedConfigIds.contains(nextGeneratedConfigId)) {
            nextGeneratedConfigId += 1
        }
        val id = nextGeneratedConfigId
        usedConfigIds.add(id)
        nextGeneratedConfigId += 1
        return id
    }

    val existingConfigsByAppId = existingConfigs
        .filter { config -> config.subsId == subsId }
        .associateBy { config -> config.appId }

    return subscription.apps
        .filter { rawApp -> rawApp.groups.isNotEmpty() }
        .map { rawApp ->
            val enable = selectedAppIds.contains(rawApp.id)
            existingConfigsByAppId[rawApp.id]?.copy(enable = enable)
                ?: AppConfig(
                    id = nextConfigId(),
                    enable = enable,
                    subsId = subsId,
                    appId = rawApp.id,
                )
        }
}

fun buildStudentGlobalDisableConfigs(
    subsId: Long,
    subscription: RawSubscription,
    existingConfigs: List<SubsConfig>,
): List<SubsConfig> {
    val existingConfigsByGroupKey = existingConfigs
        .filter { config ->
            config.subsId == subsId && config.type == SubsConfig.GlobalGroupType
        }
        .associateBy { config -> config.groupKey }

    return subscription.globalGroups.map { group ->
        val enable = shouldEnableStudentGlobalGroup(group)
        existingConfigsByGroupKey[group.key]?.copy(
            type = SubsConfig.GlobalGroupType,
            enable = enable,
            subsId = subsId,
            appId = "",
            groupKey = group.key,
            exclude = "",
        ) ?: SubsConfig(
            type = SubsConfig.GlobalGroupType,
            enable = enable,
            subsId = subsId,
            groupKey = group.key,
        )
    }
}
