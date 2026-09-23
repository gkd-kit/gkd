package li.gkd.app.feature.subscription

import kotlinx.coroutines.Dispatchers
import li.gkd.app.ui.share.EditorSaveSession
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import li.gkd.app.text.UiStrings
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.SubscriptionInputParser
import li.gkd.app.data.edit
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.style.clearJson5TransformationCache
import li.gkd.app.util.ToastUtils.toast

data class UpsertRuleGroupUiState(
    val initialGroup: RawSubscription.RawGroupProps?,
    val initialText: String,
)

class UpsertRuleGroupVm(val route: UpsertRuleGroupRoute) : BaseViewModel() {
    val groupKey = route.groupKey
    val appId = route.appId

    val isEdit = groupKey != null
    val isApp = appId != null
    val isAddAnyApp = appId == ""

    private val requiredSubscription = requiredSubscription(route.subsId)
    val uiState = requiredSubscription.buildUiState(
        initialValue = ::buildUiState,
    ) { subscription ->
        flowOf(buildUiState(subscription))
    }

    val textFlow: StateFlow<String?>
        field = MutableStateFlow(null)
    private var editBaseGroup: RawSubscription.RawGroupProps? = null

    private fun buildUiState(subscription: RawSubscription): UpsertRuleGroupUiState {
        val initialGroup = if (groupKey != null) {
            val groups = if (appId != null) {
                subscription.getAppGroups(appId)
            } else {
                subscription.globalGroups
            }
            groups.find { it.key == groupKey }
                ?: error(UiStrings.subscription_rule_missing_detail(groupKey))
        } else {
            null
        }
        return UpsertRuleGroupUiState(
            initialGroup = initialGroup,
            initialText = initialGroup?.cacheStr.orEmpty(),
        )
    }

    fun setText(text: String) {
        if (textFlow.value == null) {
            editBaseGroup = uiState.value.value?.initialGroup
        }
        textFlow.value = text
    }

    private fun requireUiState(): UpsertRuleGroupUiState =
        uiState.value.value ?: error(UiStrings.subscription_not_loaded_id(route.subsId))

    suspend fun hasTextChanged(): Boolean {
        val state = uiState.value.value ?: return false
        val text = textFlow.value ?: state.initialText
        if (!isEdit) return !text.isBlank()
        if (state.initialText == text) return false
        return withContext(Dispatchers.Default) {
            state.initialGroup?.cacheJsonObject != runCatching {
                SubscriptionInputParser.parse(text, groupKey ?: 0).jsonObject
            }.getOrNull()
        }
    }

    private val saveSession = EditorSaveSession<String?>()

    suspend fun saveRule(): String? = saveSession.save { performSave() }

    private suspend fun performSave(): String? {
        val state = requireUiState()
        val initialGroup = state.initialGroup
        val text = textFlow.value ?: state.initialText
        val baseGroup = editBaseGroup ?: initialGroup
        return withContext(Dispatchers.Default) {
            if (text.isBlank()) {
                error(UiStrings.rule_content_required)
            }
            if (text == state.initialText) {
                toast(UiStrings.rule_content_unchanged)
                return@withContext null
            }
            val input = SubscriptionInputParser.parse(text, groupKey ?: 0)
            if (input.jsonObject == initialGroup?.cacheJsonObject) {
                toast(UiStrings.rule_content_unchanged)
                return@withContext null
            }
            var addedAppId: String? = null
            if (groupKey != null) {
                if (appId != null) {
                    val newGroup = input.parseAppGroup(appId).copy(key = groupKey)
                    if (newGroup == initialGroup) {
                        toast(UiStrings.rule_content_unchanged)
                        return@withContext null
                    }
                    val originalGroup = requireNotNull(
                        baseGroup,
                    ) as RawSubscription.RawAppGroup
                    requiredSubscription.update { subscription ->
                        subscription.edit {
                            replaceAppGroup(
                                targetApp = subscription.getApp(appId),
                                groupKey = groupKey,
                                expectedGroup = originalGroup,
                                newGroup = newGroup,
                            )
                        }
                    }
                } else {
                    val newGroup = input.parseGlobalGroup().copy(key = groupKey)
                    if (newGroup == initialGroup) {
                        toast(UiStrings.rule_content_unchanged)
                        return@withContext null
                    }
                    val originalGroup = requireNotNull(
                        baseGroup,
                    ) as RawSubscription.RawGlobalGroup
                    requiredSubscription.update { subscription ->
                        subscription.edit {
                            replaceGlobalGroup(groupKey, originalGroup, newGroup)
                        }
                    }
                }
            } else {
                if (isAddAnyApp) {
                    val newApp = input.parseApp()
                    requiredSubscription.update { subscription ->
                        subscription.edit { mergeApp(newApp) }
                    }
                    addedAppId = newApp.id
                } else if (appId != null) {
                    // add specified app group
                    val newGroups = input.parseAppGroups(appId)
                    requiredSubscription.update { subscription ->
                        subscription.edit {
                            appendAppGroups(
                                targetApp = subscription.getApp(appId),
                                groups = newGroups,
                            )
                        }
                    }
                } else {
                    // add global group
                    val newGroup = input.parseGlobalGroup()
                    requiredSubscription.update { subscription ->
                        subscription.edit { appendGlobalGroup(newGroup) }
                    }
                }
            }
            if (isEdit) {
                toast(UiStrings.update_success)
            } else {
                toast(UiStrings.add_success)
            }
            addedAppId
        }
    }

    init {
        addCloseable { clearJson5TransformationCache() }
    }
}
