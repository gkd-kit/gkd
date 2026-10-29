package li.gkd.app.ui.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionEditResult
import li.gkd.app.subscription.SubscriptionFailureReason
import li.gkd.app.subscription.SubscriptionInputParser
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.navigation.UpsertRuleGroupRoute
import li.gkd.app.ui.page.EditorActionsState
import li.gkd.app.ui.page.EditorSaveResult
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
    val editorActions = EditorActionsState(scope)

    val draft: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    fun setDraft(value: String) {
        beginEditing()
        draft.value = value
    }

    val groupKey
        get() = route.groupKey

    val appId
        get() = route.appId

    val isEdit = groupKey != null
    val isApp = appId != null
    val isAddAnyApp = appId == ""

    private val requiredSubscription = RequiredSubscription(route.subsId, scope)
    val uiState =
        requiredSubscription.buildUiState(
            initialValue = ::buildUiState,
        ) { subscription ->
            flowOf(buildUiState(subscription))
        }

    private var editBaseGroup: RawSubscription.RawGroupProps? = null
    private var editingStarted = false

    private fun buildUiState(subscription: RawSubscription): UpsertRuleGroupUiState {
        val groupKey = groupKey
        val appId = appId
        val initialGroup =
            if (groupKey != null) {
                val groups =
                    if (appId != null) {
                        subscription.getAppGroups(appId)
                    } else {
                        subscription.globalGroups
                    }
                groups.find { it.key == groupKey }
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

    suspend fun hasTextChanged(text: String): Boolean {
        val state = uiState.value.value ?: return false
        if (!isEdit) return text.isNotBlank()
        if (state.initialText == text) return false
        return withContext(Dispatchers.Default) {
            when (val result = SubscriptionInputParser.parse(text, groupKey ?: 0)) {
                is SubscriptionEditResult.Rejected -> true
                is SubscriptionEditResult.Success ->
                    state.initialGroup?.cacheJsonObject != result.value.jsonObject
            }
        }
    }

    private val saveSession =
        EditorSaveSession<SubscriptionEditResult<String?>> { it is SubscriptionEditResult.Success }
    private var addedAppId: String? = null

    suspend fun saveRule(text: String): EditorSaveResult =
        when (val result = saveSession.save { performSave(text) }) {
            is SubscriptionEditResult.Rejected ->
                EditorSaveResult.Rejected(result.errorMessage.resolve())
            is SubscriptionEditResult.Success -> {
                addedAppId = result.value
                EditorSaveResult.Saved
            }
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

    private suspend fun performSave(text: String): SubscriptionEditResult<String?> =
        withContext(Dispatchers.Default) {
            val state =
                when (val current = uiState.value) {
                    Loadable.Loading ->
                        return@withContext SubscriptionEditResult.Rejected(
                            SubscriptionFailureReason.SubscriptionNotLoadedId,
                            listOf(route.subsId.toString()),
                        )
                    is Loadable.Failure -> throw current.cause
                    is Loadable.Ready -> current.value
                }
            val groupKey = groupKey
            val appId = appId
            val initialGroup = state.initialGroup
            val baseGroup = editBaseGroup ?: initialGroup
            if (groupKey != null && initialGroup == null)
                return@withContext SubscriptionEditResult.Rejected(
                    SubscriptionFailureReason.RuleMissingKey,
                    listOf(groupKey.toString()),
                )
            if (text.isBlank())
                return@withContext SubscriptionEditResult.Rejected(
                    SubscriptionFailureReason.RuleInputRequired,
                )
            if (text == state.initialText) {
                ToastUtils.show(getString(Res.string.rule_content_unchanged))
                return@withContext SubscriptionEditResult.Success(null)
            }
            val input =
                when (val parsed = SubscriptionInputParser.parse(text, groupKey ?: 0)) {
                    is SubscriptionEditResult.Rejected -> return@withContext parsed
                    is SubscriptionEditResult.Success -> parsed.value
                }
            if (input.jsonObject == initialGroup?.cacheJsonObject) {
                ToastUtils.show(getString(Res.string.rule_content_unchanged))
                return@withContext SubscriptionEditResult.Success(null)
            }
            var addedAppId: String? = null
            val saved =
                if (groupKey != null) {
                    if (appId != null) {
                        val newGroup =
                            when (val parsed = input.parseAppGroup(appId)) {
                                is SubscriptionEditResult.Rejected -> return@withContext parsed
                                is SubscriptionEditResult.Success ->
                                    parsed.value.copy(key = groupKey)
                            }
                        val original = baseGroup as RawSubscription.RawAppGroup
                        SubscriptionRepository.editSubscription(route.subsId) { subscription ->
                            subscription.editResult {
                                replaceAppGroup(
                                    subscription.getApp(
                                        appId,
                                        AppInfoRepository.snapshot?.apps?.get(appId)?.name,
                                    ),
                                    groupKey,
                                    original,
                                    newGroup,
                                )
                            }
                        }
                    } else {
                        val newGroup =
                            when (val parsed = input.parseGlobalGroup()) {
                                is SubscriptionEditResult.Rejected -> return@withContext parsed
                                is SubscriptionEditResult.Success ->
                                    parsed.value.copy(key = groupKey)
                            }
                        val original = baseGroup as RawSubscription.RawGlobalGroup
                        SubscriptionRepository.editSubscription(route.subsId) { subscription ->
                            subscription.editResult {
                                replaceGlobalGroup(groupKey, original, newGroup)
                            }
                        }
                    }
                } else if (isAddAnyApp) {
                    val app =
                        when (val parsed = input.parseApp()) {
                            is SubscriptionEditResult.Rejected -> return@withContext parsed
                            is SubscriptionEditResult.Success -> parsed.value
                        }
                    addedAppId = app.id
                    SubscriptionRepository.editSubscription(route.subsId) { subscription ->
                        subscription.editResult { mergeApp(app) }
                    }
                } else if (appId != null) {
                    val groups =
                        when (val parsed = input.parseAppGroups(appId)) {
                            is SubscriptionEditResult.Rejected -> return@withContext parsed
                            is SubscriptionEditResult.Success -> parsed.value
                        }
                    SubscriptionRepository.editSubscription(route.subsId) { subscription ->
                        subscription.editResult {
                            appendAppGroups(
                                subscription.getApp(
                                    appId,
                                    AppInfoRepository.snapshot?.apps?.get(appId)?.name,
                                ),
                                groups,
                            )
                        }
                    }
                } else {
                    val group =
                        when (val parsed = input.parseGlobalGroup()) {
                            is SubscriptionEditResult.Rejected -> return@withContext parsed
                            is SubscriptionEditResult.Success -> parsed.value
                        }
                    SubscriptionRepository.editSubscription(route.subsId) { subscription ->
                        subscription.editResult { appendGlobalGroup(group) }
                    }
                }
            when (saved) {
                is SubscriptionEditResult.Rejected -> saved
                is SubscriptionEditResult.Success -> {
                    ToastUtils.show(
                        getString(
                            if (!saved.value) Res.string.rule_content_unchanged
                            else if (isEdit) Res.string.update_success else Res.string.add_success,
                        ),
                    )
                    SubscriptionEditResult.Success(addedAppId)
                }
            }
        }

    init {
        addCloseable { clearJson5TransformationCache() }
    }
}
