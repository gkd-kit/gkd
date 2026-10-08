package li.gkd.app.ui.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.resources.Res
import li.gkd.app.resources.add_success
import li.gkd.app.resources.rule_content_required
import li.gkd.app.resources.rule_content_unchanged
import li.gkd.app.resources.update_success
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionException
import li.gkd.app.subscription.SubscriptionFailureReason
import li.gkd.app.subscription.SubscriptionInputParser
import li.gkd.app.subscription.edit
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.style.clearJson5TransformationCache
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

data class UpsertRuleGroupUiState(
    val initialGroup: RawSubscription.RawGroupProps?,
    val initialText: String,
)

class UpsertRuleGroupViewModel(
    val route: UpsertRuleGroupRoute,
) : BaseViewModel() {
    val draft: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    fun setDraft(value: String) {
        beginEditing()
        draft.value = value
    }

    val groupKey get() = route.groupKey
    val appId get() = route.appId

    val isEdit = groupKey != null
    val isApp = appId != null
    val isAddAnyApp = appId == ""

    private val requiredSubscription =
        RequiredSubscription(route.subsId, scope)
    val uiState = requiredSubscription.buildUiState(
        initialValue = ::buildUiState,
    ) { subscription ->
        flowOf(buildUiState(subscription))
    }

    private var editBaseGroup: RawSubscription.RawGroupProps? = null
    private var editingStarted = false

    private fun buildUiState(subscription: RawSubscription): UpsertRuleGroupUiState {
        val groupKey = groupKey
        val appId = appId
        val initialGroup = if (groupKey != null) {
            val groups = if (appId != null) {
                subscription.getAppGroups(appId)
            } else {
                subscription.globalGroups
            }
            groups.find { it.key == groupKey }
                ?: throw SubscriptionException(
                    SubscriptionFailureReason.RuleMissingKey, listOf(groupKey.toString())
                )
        } else {
            null
        }
        return UpsertRuleGroupUiState(
            initialGroup = initialGroup,
            initialText = initialGroup?.cacheStr.orEmpty(),
        )
    }

    private fun beginEditing() {
        if (!editingStarted) {
            editBaseGroup = uiState.value.value?.initialGroup
            editingStarted = true
        }
    }

    private fun requireUiState(): UpsertRuleGroupUiState =
        uiState.value.value ?: throw SubscriptionException(
            SubscriptionFailureReason.SubscriptionNotLoadedId, listOf(route.subsId.toString())
        )

    suspend fun hasTextChanged(text: String): Boolean {
        val state = uiState.value.value ?: return false
        if (!isEdit) return !text.isBlank()
        if (state.initialText == text) return false
        return withContext(Dispatchers.Default) {
            state.initialGroup?.cacheJsonObject != runCatching {
                SubscriptionInputParser.parse(text, groupKey ?: 0).jsonObject
            }.getOrNull()
        }
    }

    private val saveSession = EditorSaveSession<String?>()

    private var addedAppId: String? = null

    suspend fun saveRule(text: String) {
        addedAppId = saveSession.save { performSave(text) }
    }

    fun onSaved() {
        val mainVm = MainViewModel.requireCurrent()
        if (route.forward) {
            mainVm.navigator.navigate(
                if (route.appId == null) SubsGlobalGroupListRoute(route.subsId)
                else SubsAppGroupListRoute(route.subsId, addedAppId ?: route.appId),
                replaced = true,
            )
        } else mainVm.navigator.pop()
    }

    private suspend fun performSave(text: String): String? {
        val groupKey = groupKey
        val appId = appId
        val state = requireUiState()
        val initialGroup = state.initialGroup
        val baseGroup = editBaseGroup ?: initialGroup
        return withContext(Dispatchers.Default) {
            if (text.isBlank()) {
                error(getString(Res.string.rule_content_required))
            }
            if (text == state.initialText) {
                ToastUtils.show(getString(Res.string.rule_content_unchanged))
                return@withContext null
            }
            val input = SubscriptionInputParser.parse(text, groupKey ?: 0)
            if (input.jsonObject == initialGroup?.cacheJsonObject) {
                ToastUtils.show(getString(Res.string.rule_content_unchanged))
                return@withContext null
            }
            var addedAppId: String? = null
            if (groupKey != null) {
                if (appId != null) {
                    val newGroup = input.parseAppGroup(appId).copy(key = groupKey)
                    if (newGroup == initialGroup) {
                        ToastUtils.show(getString(Res.string.rule_content_unchanged))
                        return@withContext null
                    }
                    val originalGroup = requireNotNull(
                        baseGroup,
                    ) as RawSubscription.RawAppGroup
                    requiredSubscription.update { subscription ->
                        subscription.edit {
                            replaceAppGroup(
                                targetApp = subscription.getApp(
                                    appId,
                                    AppInfoRepository.snapshot?.apps?.get(appId)?.name
                                ),
                                groupKey = groupKey,
                                expectedGroup = originalGroup,
                                newGroup = newGroup,
                            )
                        }
                    }
                } else {
                    val newGroup = input.parseGlobalGroup().copy(key = groupKey)
                    if (newGroup == initialGroup) {
                        ToastUtils.show(getString(Res.string.rule_content_unchanged))
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
                                targetApp = subscription.getApp(
                                    appId,
                                    AppInfoRepository.snapshot?.apps?.get(appId)?.name
                                ),
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
                ToastUtils.show(getString(Res.string.update_success))
            } else {
                ToastUtils.show(getString(Res.string.add_success))
            }
            addedAppId
        }
    }

    init {
        addCloseable { clearJson5TransformationCache() }
    }
}
