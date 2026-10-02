package li.gkd.app.subscription

sealed interface SubscriptionResult {
    data object Busy : SubscriptionResult

    data class Success(
        val kind: SuccessKind = SuccessKind.None,
        val count: Int = 0,
    ) : SubscriptionResult

    data class Failure(
        val reason: FailureReason,
        val detail: String? = null,
        val cause: Throwable? = null,
    ) : SubscriptionResult

    enum class SuccessKind {
        None,
        Deleted,
        Added,
        Modified,
        Refreshed,
    }

    enum class FailureReason {
        DeleteData,
        DeleteFile,
        DuplicateUrl,
        Download,
        Parse,
        AlreadyExists,
        IdMismatch,
        InvalidId,
        Save,
        NetworkUnavailable,
    }
}
