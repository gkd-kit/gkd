package li.songe.gkd.store

import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.UpdateChannelOption
import li.songe.gkd.util.UpdateTimeOption

@Serializable
data class SettingsStore(
    val enableService: Boolean = true,
    val enableMatch: Boolean = true,
    val enableStatusService: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val screenshotTargetAppId: String = "",
    val screenshotEventSelector: String = "",
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = UpdateTimeOption.Everyday.value,
    val captureVolumeChange: Boolean = false,
    val toastWhenClick: Boolean = true,
    val actionToast: String = META.appName,
    val autoClearMemorySubs: Boolean = true,
    val hideSnapshotStatusBar: Boolean = false,
    val enableDarkTheme: Boolean? = null,
    val enableDynamicColor: Boolean = true,
    val showSaveSnapshotToast: Boolean = true,
    val useSystemToast: Boolean = false,
    val useCustomNotifText: Boolean = false,
    val customNotifTitle: String = META.appName,
    val customNotifText: String = "\${i}全局/\${k}应用/\${u}规则组/\${n}触发",
    val updateChannel: Int = if (META.isBeta) UpdateChannelOption.Beta.value else UpdateChannelOption.Stable.value,
    val appSort: Int = AppSortOption.ByUsedTime.value,
    val showBlockApp: Boolean = false,
    val appRuleSort: Int = RuleSortOption.ByDefault.value,
    val subsAppSort: Int = AppSortOption.ByUsedTime.value,
    val subsAppShowUninstallApp: Boolean = false,
    val subsExcludeSort: Int = AppSortOption.ByUsedTime.value,
    val subsExcludeShowInnerDisabledApp: Boolean = false,
    val subsExcludeShowBlockApp: Boolean = false,
    val subsPowerWarn: Boolean = true,
    val enableShizuku: Boolean = false,
    val enableBlockA11yAppList: Boolean = false,
    val blockA11yAppListFollowMatch: Boolean = false,
    val a11yAppSort: Int = AppSortOption.ByUsedTime.value,
)
