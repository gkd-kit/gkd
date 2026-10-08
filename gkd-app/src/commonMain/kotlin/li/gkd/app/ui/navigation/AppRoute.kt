package li.gkd.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.db.ActionLog

enum class RouteTransition {
    Standard,
    Editor,
}

fun RouteTransition.toMetadata(): Map<String, Any> = when (this) {
    RouteTransition.Standard -> emptyMap()
    RouteTransition.Editor -> GkNavigationTransitions.editor
}

@Serializable
sealed interface AppRoute : NavKey {
    val transition: RouteTransition get() = RouteTransition.Standard
}

@Serializable
data object A11YScopeAppListRoute : AppRoute

@Serializable
data class AppConfigRoute(
    val appId: String,
    val focusLog: ActionLog? = null,
) : AppRoute

@Serializable
data object BlockA11yAppListRoute : AppRoute

@Serializable
data object CrashReportRoute : AppRoute

@Serializable
data object EditBlockAppListRoute : AppRoute {
    override val transition get() = RouteTransition.Editor
}

@Serializable
data class ImagePreviewRoute(
    val title: String? = null,
    val items: List<ImagePreviewItem> = emptyList(),
) : AppRoute

@Serializable
data object PrivilegeServiceRoute : AppRoute

@Serializable
data class WebViewRoute(val initUrl: String) : AppRoute

@Serializable
data object ActionToastRoute : AppRoute {
    override val transition get() = RouteTransition.Editor
}

@Serializable
data object BlockA11ySetupRoute : AppRoute {
    override val transition get() = RouteTransition.Editor
}

@Serializable
data object HomeRoute : AppRoute

@Serializable
data object NotificationTextRoute : AppRoute {
    override val transition get() = RouteTransition.Editor
}

@Serializable
data object A11yEventLogRoute : AppRoute

@Serializable
data class ActionLogRoute(
    val subsId: Long? = null,
    val appId: String? = null,
) : AppRoute

@Serializable
data object ActivityLogRoute : AppRoute

@Serializable
data object AboutRoute : AppRoute

@Serializable
data object AdvancedPageRoute : AppRoute

@Serializable
data object WorkModeRoute : AppRoute

@Serializable
data object SnapshotPageRoute : AppRoute

@Serializable
data class SnapshotPreviewRoute(
    val snapshotId: Long,
    val snapshotIds: List<Long>,
) : AppRoute

@Serializable
data object SnapshotSettingsRoute : AppRoute

@Serializable
data class CategoryEditorRoute(val subsId: Long, val categoryKey: Int? = null) : AppRoute {
    override val transition get() = RouteTransition.Editor
}

@Serializable
data class RuleExcludeEditorRoute(
    val subsId: Long,
    val groupKey: Int,
    val appId: String? = null,
) : AppRoute {
    override val transition get() = RouteTransition.Editor
}

@Serializable
data class SubsAppGroupListRoute(
    val subsItemId: Long,
    val appId: String,
    val focusGroupKey: Int? = null,
) : AppRoute

@Serializable
data class SubsAppListRoute(val subsItemId: Long) : AppRoute

@Serializable
data class SubsCategoryGroupRoute(val subsId: Long, val categoryKey: Int) : AppRoute

@Serializable
data class SubsCategoryRoute(val subsItemId: Long) : AppRoute

@Serializable
data class SubsGlobalGroupExcludeRoute(val subsItemId: Long, val groupKey: Int) : AppRoute

@Serializable
data class SubsGlobalGroupListRoute(val subsItemId: Long, val focusGroupKey: Int? = null) : AppRoute

@Serializable
data class UpsertRuleGroupRoute(
    val subsId: Long,
    val groupKey: Int? = null,
    val appId: String? = null,
    val forward: Boolean = false,
) : AppRoute {
    override val transition get() = RouteTransition.Editor
}

@Serializable
data class ImagePreviewItem(
    val uri: String,
    val title: String? = null,
    val titles: List<String> = emptyList(),
)
