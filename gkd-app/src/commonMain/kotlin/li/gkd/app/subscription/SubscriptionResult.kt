package li.gkd.app.subscription

import li.gkd.app.error.ErrorMessage
import li.gkd.app.error.resolveMessage
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

sealed interface SubscriptionResult {
    suspend fun message(): String?

    data object Busy : SubscriptionResult {
        override suspend fun message(): String = getString(Res.string.subscription_busy_retry)
    }

    data class Success(
        val kind: SuccessKind = SuccessKind.None,
        val count: Int = 0,
    ) : SubscriptionResult {
        override suspend fun message(): String? {
            val resource = kind.messageResource ?: return null
            return if (kind == SuccessKind.Refreshed) {
                if (count > 0) getString(resource, count.toString()) else getString(Res.string.updates_none)
            } else getString(resource)
        }
    }

    data class Failure(
        val reason: FailureReason,
        val detail: String? = null,
        val cause: Throwable? = null,
    ) : SubscriptionResult {
        override suspend fun message(): String = when (reason) {
            FailureReason.InvalidId -> getString(reason.messageResource, detail.orEmpty())
            FailureReason.DeleteData, FailureReason.DeleteFile, FailureReason.Download,
            FailureReason.Parse, FailureReason.Save -> ErrorMessage.WithDetail(
                ErrorMessage.Resource(reason.messageResource),
                ErrorMessage.Text(cause?.resolveMessage() ?: detail.orEmpty()),
            ).resolve()
            else -> getString(reason.messageResource)
        }
    }

    enum class SuccessKind(val messageResource: StringResource?) {
        None(null),
        Deleted(Res.string.delete_success),
        Added(Res.string.subscription_add_success),
        Modified(Res.string.subscription_edit_success),
        Refreshed(Res.string.subscriptions_updated_count),
    }

    enum class FailureReason(val messageResource: StringResource) {
        DeleteData(Res.string.subscription_data_delete_failed),
        DeleteFile(Res.string.subscription_file_delete_cancelled),
        DuplicateUrl(Res.string.subscription_duplicate_link),
        Download(Res.string.subscription_file_download_failed),
        Parse(Res.string.subscription_file_parsing_failed),
        AlreadyExists(Res.string.subscription_exists),
        InvalidId(Res.string.subscription_id_reserved),
        Save(Res.string.subscription_file_save_failed),
        NetworkUnavailable(Res.string.network_unavailable),
    }
}
