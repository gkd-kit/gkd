package li.gkd.app.inspection

import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

enum class InspectionFailure(val errorCode: String, val messageResource: StringResource) {
    SnapshotNotFound("SNAPSHOT_NOT_FOUND", Res.string.snapshot_not_found),
    ScreenshotNotFound("SCREENSHOT_NOT_FOUND", Res.string.screenshot_not_found),
    SnapshotMissingOrDeleted("SNAPSHOT_NOT_FOUND", Res.string.snapshot_missing_or_deleted),
    InvalidSelector("INVALID_SELECTOR", Res.string.selector_invalid),
    SelectorTypeError("INVALID_SELECTOR", Res.string.selector_type_error),
    AccessibilityUnavailable("ACCESSIBILITY_UNAVAILABLE", Res.string.service_not_connected),
    AccessibilityAuthorizationRequired("ACCESSIBILITY_UNAVAILABLE", Res.string.service_unavailable_authorize),
    ScreenNodesUnavailable("SCREEN_NODES_UNAVAILABLE", Res.string.screen_nodes_unavailable),
    SnapshotNodesUnavailable("SCREEN_NODES_UNAVAILABLE", Res.string.snapshot_app_a11y_missing),
    TargetNodeNotFound("TARGET_NODE_NOT_FOUND", Res.string.selector_node_not_found),
    PlatformUnsupported("PLATFORM_UNSUPPORTED", Res.string.platform_action_unsupported),
}

class InspectionException(
    val reason: InspectionFailure,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(reason.name, cause), BusinessError {
    override val errorCode: String get() = reason.errorCode
    override val errorMessage: ErrorMessage
        get() = ErrorMessage.Resource(
            reason.messageResource,
            if (reason == InspectionFailure.SelectorTypeError) listOf(ErrorMessage.Text(detail.orEmpty())) else emptyList(),
        )
}
