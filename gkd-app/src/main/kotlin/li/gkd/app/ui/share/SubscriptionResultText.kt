package li.gkd.app.ui.share

import li.gkd.app.text.UiStrings
import li.gkd.app.data.subscription.SubscriptionResult

val SubscriptionResult.message: String?
    get() = when (this) {
        SubscriptionResult.Busy -> UiStrings.subscription_busy_retry
        is SubscriptionResult.Success -> when (kind) {
            SubscriptionResult.SuccessKind.None -> null
            SubscriptionResult.SuccessKind.Deleted -> UiStrings.delete_success
            SubscriptionResult.SuccessKind.Added -> UiStrings.subscription_add_success
            SubscriptionResult.SuccessKind.Modified -> UiStrings.subscription_edit_success
            SubscriptionResult.SuccessKind.Refreshed -> {
                if (count > 0) UiStrings.subscriptions_updated_count(count) else UiStrings.updates_none
            }
        }

        is SubscriptionResult.Failure -> when (reason) {
            SubscriptionResult.FailureReason.DeleteData -> detailMessage(UiStrings.subscription_data_delete_failed, detail)
            SubscriptionResult.FailureReason.DeleteFile ->
                detailMessage(UiStrings.subscription_file_delete_cancelled, detail)
            SubscriptionResult.FailureReason.DuplicateUrl -> UiStrings.subscription_duplicate_link
            SubscriptionResult.FailureReason.Download -> detailMessage(UiStrings.subscription_file_download_failed, detail)
            SubscriptionResult.FailureReason.Parse -> detailMessage(UiStrings.subscription_file_parsing_failed, detail)
            SubscriptionResult.FailureReason.AlreadyExists -> UiStrings.subscription_exists
            SubscriptionResult.FailureReason.IdMismatch -> UiStrings.subscription_id_mismatched
            SubscriptionResult.FailureReason.InvalidId -> UiStrings.subscription_id_reserved(detail)
            SubscriptionResult.FailureReason.Save -> detailMessage(UiStrings.subscription_file_save_failed, detail)
            SubscriptionResult.FailureReason.NetworkUnavailable -> UiStrings.network_unavailable
        }
    }

private fun detailMessage(message: String, detail: String?): String =
    if (detail.isNullOrBlank()) message else "$message\n$detail"
