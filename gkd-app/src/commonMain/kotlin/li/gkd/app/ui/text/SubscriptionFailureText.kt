package li.gkd.app.ui.text

import androidx.compose.runtime.Composable
import li.gkd.app.resources.Res
import li.gkd.app.resources.category_config_conflict
import li.gkd.app.resources.category_key_exhausted
import li.gkd.app.resources.category_missing
import li.gkd.app.resources.category_name_duplicate
import li.gkd.app.resources.category_name_required
import li.gkd.app.resources.exclusion_config_conflict
import li.gkd.app.resources.file_delete_failed
import li.gkd.app.resources.file_too_large
import li.gkd.app.resources.format_invalid_detail
import li.gkd.app.resources.page_exclusion_conflict
import li.gkd.app.resources.remote_category_delete_unsupported
import li.gkd.app.resources.remote_category_edit_unsupported
import li.gkd.app.resources.rule_app_id_mismatch
import li.gkd.app.resources.rule_edit_conflict
import li.gkd.app.resources.rule_id_required
import li.gkd.app.resources.rule_input_required
import li.gkd.app.resources.rule_invalid_detail
import li.gkd.app.resources.rule_invalid_position
import li.gkd.app.resources.rule_key_mismatch
import li.gkd.app.resources.rule_missing
import li.gkd.app.resources.rule_name_duplicate
import li.gkd.app.resources.rule_object_required
import li.gkd.app.resources.rule_switch_conflict
import li.gkd.app.resources.selected_rules_missing
import li.gkd.app.resources.selector_invalid_detail
import li.gkd.app.resources.subscription_app_missing
import li.gkd.app.resources.subscription_content_conflict
import li.gkd.app.resources.subscription_file_id_mismatch
import li.gkd.app.resources.subscription_file_missing
import li.gkd.app.resources.subscription_file_parse_failed
import li.gkd.app.resources.subscription_id_immutable
import li.gkd.app.resources.subscription_item_id_mismatch
import li.gkd.app.resources.subscription_missing
import li.gkd.app.resources.subscription_missing_id
import li.gkd.app.resources.subscription_not_loaded
import li.gkd.app.resources.subscription_not_loaded_id
import li.gkd.app.resources.subscription_preview_conflict
import li.gkd.app.resources.subscription_rule_missing_detail
import li.gkd.app.resources.subscription_unloaded_or_missing
import li.gkd.app.resources.subscription_update_url_request_failed
import li.gkd.app.resources.subscription_updated_id_mismatch
import li.gkd.app.resources.text_parse_failed
import li.gkd.app.subscription.SubscriptionException
import li.gkd.app.subscription.SubscriptionFailureReason
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val SubscriptionFailureReason.resource: StringResource
    get() = when (this) {
        SubscriptionFailureReason.CategoryConfigConflict -> Res.string.category_config_conflict
        SubscriptionFailureReason.CategoryKeyExhausted -> Res.string.category_key_exhausted
        SubscriptionFailureReason.CategoryMissing -> Res.string.category_missing
        SubscriptionFailureReason.CategoryNameDuplicate -> Res.string.category_name_duplicate
        SubscriptionFailureReason.CategoryNameRequired -> Res.string.category_name_required
        SubscriptionFailureReason.ExclusionConfigConflict -> Res.string.exclusion_config_conflict
        SubscriptionFailureReason.FileDeleteFailed -> Res.string.file_delete_failed
        SubscriptionFailureReason.FormatInvalidDetail -> Res.string.format_invalid_detail
        SubscriptionFailureReason.PageExclusionConflict -> Res.string.page_exclusion_conflict
        SubscriptionFailureReason.RemoteCategoryDeleteUnsupported -> Res.string.remote_category_delete_unsupported
        SubscriptionFailureReason.RemoteCategoryEditUnsupported -> Res.string.remote_category_edit_unsupported
        SubscriptionFailureReason.RuleAppIdMismatch -> Res.string.rule_app_id_mismatch
        SubscriptionFailureReason.RuleEditConflict -> Res.string.rule_edit_conflict
        SubscriptionFailureReason.RuleIdRequired -> Res.string.rule_id_required
        SubscriptionFailureReason.RuleInputRequired -> Res.string.rule_input_required
        SubscriptionFailureReason.RuleInvalidDetail -> Res.string.rule_invalid_detail
        SubscriptionFailureReason.RuleInvalidPosition -> Res.string.rule_invalid_position
        SubscriptionFailureReason.RuleKeyMismatch -> Res.string.rule_key_mismatch
        SubscriptionFailureReason.RuleMissing -> Res.string.rule_missing
        SubscriptionFailureReason.RuleMissingKey -> Res.string.subscription_rule_missing_detail
        SubscriptionFailureReason.RuleNameDuplicate -> Res.string.rule_name_duplicate
        SubscriptionFailureReason.RuleObjectRequired -> Res.string.rule_object_required
        SubscriptionFailureReason.RuleSwitchConflict -> Res.string.rule_switch_conflict
        SubscriptionFailureReason.SelectedRulesMissing -> Res.string.selected_rules_missing
        SubscriptionFailureReason.SelectorInvalidDetail -> Res.string.selector_invalid_detail
        SubscriptionFailureReason.SubscriptionAppMissing -> Res.string.subscription_app_missing
        SubscriptionFailureReason.SubscriptionContentConflict -> Res.string.subscription_content_conflict
        SubscriptionFailureReason.SubscriptionFileIdMismatch -> Res.string.subscription_file_id_mismatch
        SubscriptionFailureReason.SubscriptionFileMissing -> Res.string.subscription_file_missing
        SubscriptionFailureReason.SubscriptionFileParseFailed -> Res.string.subscription_file_parse_failed
        SubscriptionFailureReason.SubscriptionIdImmutable -> Res.string.subscription_id_immutable
        SubscriptionFailureReason.SubscriptionItemIdMismatch -> Res.string.subscription_item_id_mismatch
        SubscriptionFailureReason.SubscriptionMissing -> Res.string.subscription_missing
        SubscriptionFailureReason.SubscriptionMissingId -> Res.string.subscription_missing_id
        SubscriptionFailureReason.SubscriptionNotLoaded -> Res.string.subscription_not_loaded
        SubscriptionFailureReason.SubscriptionNotLoadedId -> Res.string.subscription_not_loaded_id
        SubscriptionFailureReason.SubscriptionPreviewConflict -> Res.string.subscription_preview_conflict
        SubscriptionFailureReason.SubscriptionUnloadedOrMissing -> Res.string.subscription_unloaded_or_missing
        SubscriptionFailureReason.SubscriptionUpdateUrlRequestFailed -> Res.string.subscription_update_url_request_failed
        SubscriptionFailureReason.SubscriptionUpdatedIdMismatch -> Res.string.subscription_updated_id_mismatch
        SubscriptionFailureReason.TextParseFailed -> Res.string.text_parse_failed
    }

suspend fun SubscriptionException.localizedMessage(): String {
    val detail = cause?.subscriptionMessage()
    val text = getString(reason.resource, *messageArguments(detail))
    return withCauseDetail(text, detail)
}

@Composable
fun SubscriptionException.messageResource(): String {
    val detail = cause?.subscriptionMessageResource()
    val text = stringResource(reason.resource, *messageArguments(detail))
    return withCauseDetail(text, detail)
}

private val SubscriptionException.embedsCause: Boolean
    get() = reason == SubscriptionFailureReason.RuleInvalidDetail ||
        reason == SubscriptionFailureReason.FormatInvalidDetail

private fun SubscriptionException.messageArguments(detail: String?): Array<String> =
    (arguments + if (embedsCause) listOf(detail.orEmpty()) else emptyList()).toTypedArray()

private fun SubscriptionException.withCauseDetail(text: String, detail: String?): String =
    if (embedsCause || detail.isNullOrBlank()) text else "$text\n$detail"

/** Bridge for existing synchronous event sinks; rendering uses messageResource instead. */
fun SubscriptionException.messageSync(): String {
    val detail = cause?.displayMessage()
    return withCauseDetail(reason.resource.getSync(*messageArguments(detail)), detail)
}

/** Resolves business errors at the rendering boundary, including persistence wrappers. */
@Composable
fun Throwable.subscriptionMessageResource(): String? = when (this) {
    is SubscriptionException -> messageResource()
    is li.gkd.app.subscription.SubscriptionPersistence.DeleteException -> cause?.subscriptionMessageResource() ?: toString()
    is li.gkd.app.storage.StorageException -> stringResource(issue.resource, *arguments.map { it.toString() }.toTypedArray())
    is li.gkd.app.storage.FileTooLargeException -> stringResource(Res.string.file_too_large)
    else -> message ?: toString()
}

suspend fun Throwable.subscriptionMessage(): String = when (this) {
    is SubscriptionException -> localizedMessage()
    is li.gkd.app.subscription.SubscriptionPersistence.DeleteException -> cause?.subscriptionMessage() ?: toString()
    else -> displayMessage()
}
