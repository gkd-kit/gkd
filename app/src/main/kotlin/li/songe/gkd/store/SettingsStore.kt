package li.songe.gkd.store

import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.SortTypeOption
import li.songe.gkd.util.UpdateChannelOption
import li.songe.gkd.util.UpdateTimeOption

@Serializable
data class SettingsStore(
    val enableService: Boolean = true,
    val enableMatch: Boolean = true,
    val enableStatusService: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = UpdateTimeOption.Everyday.value,
    val captureVolumeChange: Boolean = false,
    val toastWhenClick: Boolean = true,
    val clickToast: String = META.appName,
    val autoClearMemorySubs: Boolean = true,
    val hideSnapshotStatusBar: Boolean = false,
    val enableDarkTheme: Boolean? = null,
    val enableDynamicColor: Boolean = true,
    val enableAbFloatWindow: Boolean = true,
    val showSaveSnapshotToast: Boolean = true,
    val useSystemToast: Boolean = false,
    val useCustomNotifText: Boolean = false,
    val customNotifText: String = "\${i}全局/\${k}应用/\${u}规则组/\${n}触发",
    val enableActivityLog: Boolean = false,
    val updateChannel: Int = if (META.versionName.contains("beta")) UpdateChannelOption.Beta.value else UpdateChannelOption.Stable.value,
    val sortType: Int = SortTypeOption.SortByName.value,
    val showSystemApp: Boolean = true,
    val showHiddenApp: Boolean = false,
    val appRuleSortType: Int = RuleSortOption.Default.value,
    val appShowInnerDisable: Boolean = false,
    val subsAppSortType: Int = SortTypeOption.SortByName.value,
    val subsAppShowUninstallApp: Boolean = false,
    val subsExcludeSortType: Int = SortTypeOption.SortByName.value,
    val subsExcludeShowSystemApp: Boolean = true,
    val subsExcludeShowHiddenApp: Boolean = false,
    val subsExcludeShowDisabledApp: Boolean = false,
    val subsPowerWarn: Boolean = true,
)
