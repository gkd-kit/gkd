package li.gkd.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import li.gkd.db.ActionLog

@Serializable
sealed interface AppRoute : NavKey

@Serializable
@SerialName("li.gkd.app.ui.A11YScopeAppListRoute")
data object A11YScopeAppListRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.AppConfigRoute")
data class AppConfigRoute(
    val appId: String,
    val focusLog: ActionLog? = null,
) : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.BlockA11yAppListRoute")
data object BlockA11yAppListRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.CrashReportRoute")
data object CrashReportRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.EditBlockAppListRoute")
data object EditBlockAppListRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.ImagePreviewRoute")
data class ImagePreviewRoute(
    val title: String? = null,
    val items: List<ImagePreviewItem> = emptyList(),
) : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.PrivilegeServiceRoute")
data object PrivilegeServiceRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.WebViewRoute")
data class WebViewRoute(val initUrl: String) : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.home.ActionToastRoute")
data object ActionToastRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.home.BlockA11ySetupRoute")
data object BlockA11ySetupRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.home.HomeRoute")
data object HomeRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.home.NotificationTextRoute")
data object NotificationTextRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.log.A11yEventLogRoute")
data object A11yEventLogRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.log.ActionLogRoute")
data class ActionLogRoute(
    val subsId: Long? = null,
    val appId: String? = null,
) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.log.ActivityLogRoute")
data object ActivityLogRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.settings.AboutRoute")
data object AboutRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.settings.AdvancedPageRoute")
data object AdvancedPageRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.settings.WorkModeRoute")
data object WorkModeRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.snapshot.SnapshotPageRoute")
data object SnapshotPageRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.snapshot.SnapshotPreviewRoute")
data class SnapshotPreviewRoute(
    val snapshotId: Long,
    val snapshotIds: List<Long>,
) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.snapshot.SnapshotSettingsRoute")
data object SnapshotSettingsRoute : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.CategoryEditorRoute")
data class CategoryEditorRoute(val subsId: Long, val categoryKey: Int? = null) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.RuleExcludeEditorRoute")
data class RuleExcludeEditorRoute(
    val subsId: Long,
    val groupKey: Int,
    val appId: String? = null,
) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.SubsAppGroupListRoute")
data class SubsAppGroupListRoute(
    val subsItemId: Long,
    val appId: String,
    val focusGroupKey: Int? = null,
) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.SubsAppListRoute")
data class SubsAppListRoute(val subsItemId: Long) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.SubsCategoryGroupRoute")
data class SubsCategoryGroupRoute(val subsId: Long, val categoryKey: Int) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.SubsCategoryRoute")
data class SubsCategoryRoute(val subsItemId: Long) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.SubsGlobalGroupExcludeRoute")
data class SubsGlobalGroupExcludeRoute(val subsItemId: Long, val groupKey: Int) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.SubsGlobalGroupListRoute")
data class SubsGlobalGroupListRoute(val subsItemId: Long, val focusGroupKey: Int? = null) : AppRoute

@Serializable
@SerialName("li.gkd.app.feature.subscription.UpsertRuleGroupRoute")
data class UpsertRuleGroupRoute(
    val subsId: Long,
    val groupKey: Int? = null,
    val appId: String? = null,
    val forward: Boolean = false,
) : AppRoute

@Serializable
@SerialName("li.gkd.app.ui.ImagePreviewItem")
data class ImagePreviewItem(
    val uri: String,
    val title: String? = null,
    val titles: List<String> = emptyList(),
)
