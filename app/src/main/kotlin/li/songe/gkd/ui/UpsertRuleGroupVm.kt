package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubscriptionInputParser
import li.songe.gkd.data.edit
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.style.clearJson5TransformationCache
import li.songe.gkd.util.toast

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
                ?: error("订阅规则不存在: $groupKey")
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
        uiState.value.value ?: error("订阅尚未加载: ${route.subsId}")

    fun hasTextChanged(): Boolean {
        val state = uiState.value.value ?: return false
        val text = textFlow.value ?: state.initialText
        if (!isEdit) return !text.isBlank()
        if (state.initialText == text) return false
        return state.initialGroup?.cacheJsonObject !=
            runCatching {
                SubscriptionInputParser.parse(text, groupKey ?: 0).jsonObject
            }.getOrNull()
    }

    suspend fun saveRule(): String? {
        val state = requireUiState()
        val initialGroup = state.initialGroup
        val text = textFlow.value ?: state.initialText
        if (text.isBlank()) {
            error("规则不能为空")
        }
        if (text == state.initialText) {
            toast("规则无变动")
            return null
        }
        val input = SubscriptionInputParser.parse(text, groupKey ?: 0)
        if (input.jsonObject == initialGroup?.cacheJsonObject) {
            toast("规则无变动")
            return null
        }
        var addedAppId: String? = null
        if (groupKey != null) {
            if (appId != null) {
                val newGroup = input.parseAppGroup(appId).copy(key = groupKey)
                if (newGroup == initialGroup) {
                    toast("规则无变动")
                    return null
                }
                val originalGroup = requireNotNull(
                    editBaseGroup ?: initialGroup,
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
                    toast("规则无变动")
                    return null
                }
                val originalGroup = requireNotNull(
                    editBaseGroup ?: initialGroup,
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
            toast("更新成功")
        } else {
            toast("添加成功")
        }
        return addedAppId
    }

    init {
        addCloseable { clearJson5TransformationCache() }
    }
}
