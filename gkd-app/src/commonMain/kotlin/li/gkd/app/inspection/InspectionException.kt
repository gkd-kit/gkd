package li.gkd.app.inspection

enum class InspectionFailure {
    SnapshotNotFound,
    ScreenshotNotFound,
    SnapshotMissingOrDeleted,
    InvalidSelector,
    SelectorTypeError,
    AccessibilityUnavailable,
    AccessibilityAuthorizationRequired,
    ScreenNodesUnavailable,
    SnapshotNodesUnavailable,
    TargetNodeNotFound,
    PlatformUnsupported,
}

class InspectionException(
    val reason: InspectionFailure,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(reason.name, cause)
