package li.gkd.app.subscription

import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

/** Business failures retain their identity and diagnostic cause until the display boundary. */
class SubscriptionException(
    val reason: SubscriptionFailureReason,
    val arguments: List<String> = emptyList(),
    cause: Throwable? = null,
) : IllegalStateException(
    reason.name + if (arguments.isEmpty()) "" else arguments.joinToString(prefix = ": "),
    cause,
), BusinessError {
    private val inputParseError: SubscriptionParseException?
        get() = if (reason == SubscriptionFailureReason.RuleInvalidDetail) cause as? SubscriptionParseException else null

    override val errorCode: String
        get() = when {
            inputParseError != null || reason == SubscriptionFailureReason.FormatInvalidDetail -> "INVALID_SUBSCRIPTION"
            reason == SubscriptionFailureReason.SelectorInvalidDetail -> "INVALID_SELECTOR"
            else -> reason.name
        }

    override val errorPath: String?
        get() = inputParseError?.path ?: if (reason == SubscriptionFailureReason.FormatInvalidDetail) "$" else null

    override val errorMessage: ErrorMessage
        get() {
            // A nested parser failure keeps its field path in the business failure's detail.
            val detail = cause?.let {
                when (it) {
                    is SubscriptionParseException -> ErrorMessage.Text(it.message.orEmpty())
                    is BusinessError -> it.errorMessage
                    else -> ErrorMessage.Text(it.message ?: it.toString())
                }
            }
            val embedsCause = reason == SubscriptionFailureReason.RuleInvalidDetail ||
                reason == SubscriptionFailureReason.FormatInvalidDetail
            val parameters = arguments.map { ErrorMessage.Text(it) } +
                if (embedsCause) listOf(detail ?: ErrorMessage.Text("")) else emptyList()
            val text = ErrorMessage.Resource(reason.messageResource, parameters)
            return if (embedsCause || detail == null) text else ErrorMessage.WithDetail(text, detail)
        }
}

enum class SubscriptionFailureReason(val messageResource: StringResource) {
    CategoryConfigConflict(Res.string.category_config_conflict),
    CategoryKeyExhausted(Res.string.category_key_exhausted),
    CategoryMissing(Res.string.category_missing),
    CategoryNameDuplicate(Res.string.category_name_duplicate),
    CategoryNameRequired(Res.string.category_name_required),
    ExclusionConfigConflict(Res.string.exclusion_config_conflict),
    FileDeleteFailed(Res.string.file_delete_failed),
    FormatInvalidDetail(Res.string.format_invalid_detail),
    PageExclusionConflict(Res.string.page_exclusion_conflict),
    RemoteCategoryDeleteUnsupported(Res.string.remote_category_delete_unsupported),
    RemoteCategoryEditUnsupported(Res.string.remote_category_edit_unsupported),
    RuleAppIdMismatch(Res.string.rule_app_id_mismatch),
    RuleEditConflict(Res.string.rule_edit_conflict),
    RuleIdRequired(Res.string.rule_id_required),
    RuleInputRequired(Res.string.rule_input_required),
    RuleInvalidDetail(Res.string.rule_invalid_detail),
    RuleInvalidPosition(Res.string.rule_invalid_position),
    RuleKeyMismatch(Res.string.rule_key_mismatch),
    RuleMissing(Res.string.rule_missing),
    RuleMissingKey(Res.string.subscription_rule_missing_detail),
    RuleNameDuplicate(Res.string.rule_name_duplicate),
    RuleObjectRequired(Res.string.rule_object_required),
    RuleSwitchConflict(Res.string.rule_switch_conflict),
    SelectedRulesMissing(Res.string.selected_rules_missing),
    SelectorInvalidDetail(Res.string.selector_invalid_detail),
    SubscriptionAppMissing(Res.string.subscription_app_missing),
    SubscriptionContentConflict(Res.string.subscription_content_conflict),
    SubscriptionFileIdMismatch(Res.string.subscription_file_id_mismatch),
    SubscriptionFileMissing(Res.string.subscription_file_missing),
    SubscriptionFileParseFailed(Res.string.subscription_file_parse_failed),
    SubscriptionIdImmutable(Res.string.subscription_id_immutable),
    SubscriptionItemIdMismatch(Res.string.subscription_item_id_mismatch),
    SubscriptionMissing(Res.string.subscription_missing),
    SubscriptionMissingId(Res.string.subscription_missing_id),
    SubscriptionNotLoaded(Res.string.subscription_not_loaded),
    SubscriptionNotLoadedId(Res.string.subscription_not_loaded_id),
    SubscriptionPreviewConflict(Res.string.subscription_preview_conflict),
    SubscriptionUnloadedOrMissing(Res.string.subscription_unloaded_or_missing),
    SubscriptionUpdateUrlRequestFailed(Res.string.subscription_update_url_request_failed),
    SubscriptionUpdatedIdMismatch(Res.string.subscription_updated_id_mismatch),
    TextParseFailed(Res.string.text_parse_failed),
}
