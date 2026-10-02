package li.gkd.app.settings

import kotlinx.serialization.Serializable
import li.gkd.app.app.AppGroupFlags
import li.gkd.app.app.AppSort
import li.gkd.app.rule.RuleSort
import li.gkd.app.snapshot.SnapshotDisplayMode

@Serializable
data class SettingsStore(
    val enableAutomator: Boolean = false,
    val automatorMode: Int = AutomatorMode.A11y.value,
    val enableMatch: Boolean = true,
    val enableStatusService: Boolean = false,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val screenshotTargetAppId: String = "",
    val screenshotEventSelector: String = "",
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = SubscriptionUpdateInterval.Daily,
    val captureVolumeChange: Boolean = false,
    val toastWhenClick: Boolean = true,
    val actionToast: String = "GKD",
    val autoClearMemorySubs: Boolean = false,
    val hideSnapshotStatusBar: Boolean = false,
    val autoSaveSnapshotToDownloads: Boolean = false,
    val enableDarkTheme: Boolean? = null,
    val enableDynamicColor: Boolean = true,
    val useSystemToast: Boolean = false,
    val useCustomNotifText: Boolean = false,
    val customNotifTitle: String = "GKD",
    val customNotifText: String = $$"${i}全局/${k}应用/${u}规则/${n}触发",
    val updateChannel: Int = UpdateChannel.Stable.value,
    val appSort: Int = AppSort.ByUsedTime.value,
    val showBlockApp: Boolean = true,
    val appRuleSort: Int = RuleSort.ByDefault.value,
    val subsAppSort: Int = AppSort.ByUsedTime.value,
    val subsCategorySort: Int = AppSort.ByUsedTime.value,
    val subsAppShowUninstall: Boolean = false,
    val subsAppGroupType: Int = AppGroupFlags.Installed,
    val subsCategoryGroupType: Int = AppGroupFlags.Installed,
    val subsAppShowBlock: Boolean = false,
    val subsCategoryShowBlock: Boolean = false,
    val subsExcludeSort: Int = AppSort.ByUsedTime.value,
    val subsExcludeShowBlockApp: Boolean = true,
    val subsExcludeShowInnerDisabledApp: Boolean = true,
    val subsPowerWarn: Boolean = true,
    val enableBlockA11yAppList: Boolean = false,
    val blockA11yAppListFollowMatch: Boolean = true,
    val a11yAppSort: Int = AppSort.ByUsedTime.value,
    val a11yScopeAppSort: Int = AppSort.ByUsedTime.value,
    val appGroupType: Int = AppGroupFlags.Installed,
    val a11yAppGroupType: Int = appGroupType,
    val a11yScopeAppGroupType: Int = appGroupType,
    val subsExcludeAppGroupType: Int = appGroupType,
    val showDisabledRule: Boolean = true,
    val snapshotDisplayMode: Int = SnapshotDisplayMode.ByTime.value,
) {
    val useA11y get() = automatorMode == AutomatorMode.A11y.value
    val useAutomation get() = automatorMode == AutomatorMode.Automation.value
}
