package li.gkd.app.inspection

import li.gkd.app.resources.*
import org.jetbrains.compose.resources.getString

suspend fun InspectionException.userMessage(): String = when (reason) {
    InspectionFailure.SnapshotNotFound -> getString(Res.string.snapshot_not_found)
    InspectionFailure.ScreenshotNotFound -> getString(Res.string.screenshot_not_found)
    InspectionFailure.SnapshotMissingOrDeleted -> getString(Res.string.snapshot_missing_or_deleted)
    InspectionFailure.InvalidSelector -> getString(Res.string.selector_invalid)
    InspectionFailure.SelectorTypeError -> getString(Res.string.selector_type_error, detail.orEmpty())
    InspectionFailure.AccessibilityUnavailable -> getString(Res.string.service_not_connected)
    InspectionFailure.AccessibilityAuthorizationRequired -> getString(Res.string.service_unavailable_authorize)
    InspectionFailure.ScreenNodesUnavailable -> getString(Res.string.screen_nodes_unavailable)
    InspectionFailure.SnapshotNodesUnavailable -> getString(Res.string.snapshot_app_a11y_missing)
    InspectionFailure.TargetNodeNotFound -> getString(Res.string.selector_node_not_found)
    InspectionFailure.PlatformUnsupported -> getString(Res.string.platform_action_unsupported)
}
