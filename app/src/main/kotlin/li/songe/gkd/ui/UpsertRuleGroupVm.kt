package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.style.clearJson5TransformationCache
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.toast
import li.songe.json5.Json5

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
            runCatching { Json5.parseToJson5Element(text) }.getOrNull()
    }


    var addAppId: String? = null

    suspend fun saveRule() {
        val state = requireUiState()
        val initialGroup = state.initialGroup
        val text = textFlow.value ?: state.initialText
        if (text.isBlank()) {
            error("规则不能为空")
        }
        if (text == state.initialText) {
            toast("规则无变动")
            return
        }
        var jsonObject = runCatching { Json5.parseToJson5Element(text) }.run {
            if (isFailure) {
                error("非法格式\n${exceptionOrNull()?.message}")
            }
            getOrThrow()
        }
        if (jsonObject !is JsonObject) {
            error("规则应为对象格式")
        }
        // 自动填充 key
        if (jsonObject["name"] != null && jsonObject["key"] == null) {
            jsonObject = JsonObject(jsonObject + mapOf("key" to JsonPrimitive(groupKey ?: 0)))
        }
        if (jsonObject["id"] is JsonPrimitive && jsonObject["groups"] is JsonArray) {
            val groups = jsonObject["groups"] as JsonArray
            val newGroups = groups.map {
                if (it is JsonObject && it["name"] != null && it["key"] == null) {
                    JsonObject(it + mapOf("key" to JsonPrimitive(groupKey ?: 0)))
                } else {
                    it
                }
            }
            jsonObject = JsonObject(mapOf("groups" to JsonArray(newGroups)) + jsonObject)
        }

        if (jsonObject == initialGroup?.cacheJsonObject) {
            toast("规则无变动")
            return
        }
        if (groupKey != null) {
            var newGroup = try {
                if (appId != null) {
                    if (jsonObject["groups"] is JsonArray) {
                        val id = jsonObject["id"] ?: error("缺少id")
                        if (!(id is JsonPrimitive && id.isString && id.content == appId)) {
                            error("id与当前应用不一致")
                        }
                        RawSubscription.parseApp(jsonObject).let { newApp ->
                            if (newApp.groups.isEmpty()) {
                                error("至少输入一个规则")
                            }
                            newApp.groups.first()
                        }
                    } else {
                        null
                    } ?: RawSubscription.parseAppGroup(jsonObject)
                } else {
                    RawSubscription.parseGlobalGroup(jsonObject)
                }
            } catch (e: Exception) {
                LogUtils.d(e)
                error("非法规则\n${e.message}")
            }
            newGroup.errorDesc?.let(::error)
            if (newGroup.key != groupKey) {
                // 自动修正 key 与原来一致
                newGroup = when (newGroup) {
                    is RawSubscription.RawAppGroup -> newGroup.copy(key = groupKey)
                    is RawSubscription.RawGlobalGroup -> newGroup.copy(key = groupKey)
                }
            }
            if (newGroup == initialGroup) {
                toast("规则无变动")
                return
            }
            val originalGroup = requireNotNull(editBaseGroup ?: initialGroup)
            requiredSubscription.update { subscription ->
                if (appId != null) {
                    newGroup as RawSubscription.RawAppGroup
                    val appIndex = subscription.apps.indexOfFirst { it.id == appId }
                    if (appIndex < 0) error("应用不存在")
                    val app = subscription.apps[appIndex]
                    val groupIndex = app.groups.indexOfFirst { it.key == groupKey }
                    if (groupIndex < 0) error("规则已不存在")
                    if (app.groups[groupIndex] != originalGroup) {
                        error("规则已发生变化，请重新编辑")
                    }
                    subscription.copy(
                        apps = subscription.apps.toMutableList().apply {
                            set(
                                appIndex,
                                app.copy(
                                    groups = app.groups.toMutableList().apply {
                                        set(groupIndex, newGroup)
                                    },
                                ),
                            )
                        },
                    )
                } else {
                    newGroup as RawSubscription.RawGlobalGroup
                    val groupIndex = subscription.globalGroups.indexOfFirst {
                        it.key == groupKey
                    }
                    if (groupIndex < 0) error("规则已不存在")
                    if (subscription.globalGroups[groupIndex] != originalGroup) {
                        error("规则已发生变化，请重新编辑")
                    }
                    subscription.copy(
                        globalGroups = subscription.globalGroups.toMutableList().apply {
                            set(groupIndex, newGroup)
                        },
                    )
                }
            }
        } else {
            if (isAddAnyApp) {
                val newApp = try {
                    RawSubscription.parseApp(jsonObject).apply {
                        if (groups.isEmpty()) {
                            error("至少输入一个规则")
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.d(e)
                    error("非法规则\n${e.message}")
                }
                requiredSubscription.update { subscription ->
                    val appIndex = subscription.apps.indexOfFirst { it.id == newApp.id }
                    if (appIndex < 0) {
                        subscription.copy(apps = subscription.apps + newApp)
                    } else {
                        val oldApp = subscription.apps[appIndex]
                        newApp.groups.forEach { group ->
                            checkGroupKeyName(oldApp.groups, group)
                        }
                        val usedKeys = oldApp.groups.mapTo(mutableSetOf()) { it.key }
                        val newGroups = newApp.groups.map { group ->
                            if (group.key in usedKeys) {
                                val newKey = requireNotNull(usedKeys.maxOrNull()) + 1
                                group.copy(key = newKey).also { usedKeys.add(newKey) }
                            } else {
                                group.also { usedKeys.add(it.key) }
                            }
                        }
                        subscription.copy(
                            apps = subscription.apps.toMutableList().apply {
                                set(appIndex, oldApp.copy(groups = oldApp.groups + newGroups))
                            },
                        )
                    }
                }
                addAppId = newApp.id
            } else if (appId != null) {
                // add specified app group
                val newGroups = try {
                    if (jsonObject["groups"] is JsonArray) {
                        val id = jsonObject["id"] ?: error("缺少id")
                        if (!(id is JsonPrimitive && id.isString && id.content == appId)) {
                            error("id与当前应用不一致")
                        }
                        RawSubscription.parseApp(jsonObject).apply {
                            if (groups.isEmpty()) {
                                error("至少输入一个规则")
                            }
                        }.groups
                    } else {
                        null
                    } ?: listOf(RawSubscription.parseAppGroup(jsonObject))
                } catch (e: Exception) {
                    LogUtils.d(e)
                    error("非法规则\n${e.message}")
                }
                newGroups.forEach { g ->
                    g.errorDesc?.let { error(it) }
                }
                requiredSubscription.update { subscription ->
                    val appIndex = subscription.apps.indexOfFirst { it.id == appId }
                    if (appIndex < 0) error("应用不存在")
                    val oldApp = subscription.apps[appIndex]
                    newGroups.forEach { group ->
                        checkGroupKeyName(oldApp.groups, group)
                    }
                    val usedKeys = oldApp.groups.mapTo(mutableSetOf()) { it.key }
                    val normalizedGroups = newGroups.map { group ->
                        if (group.key in usedKeys) {
                            val newKey = requireNotNull(usedKeys.maxOrNull()) + 1
                            group.copy(key = newKey).also { usedKeys.add(newKey) }
                        } else {
                            group.also { usedKeys.add(it.key) }
                        }
                    }
                    subscription.copy(
                        apps = subscription.apps.toMutableList().apply {
                            set(
                                appIndex,
                                oldApp.copy(groups = oldApp.groups + normalizedGroups),
                            )
                        },
                    )
                }
            } else {
                // add global group
                val newGroup = try {
                    RawSubscription.parseGlobalGroup(jsonObject)
                } catch (e: Exception) {
                    LogUtils.d(e)
                    error("非法规则\n${e.message}")
                }
                requiredSubscription.update { subscription ->
                    checkGroupKeyName(subscription.globalGroups, newGroup)
                    val normalizedGroup = if (
                        subscription.globalGroups.any { it.key == newGroup.key }
                    ) {
                        newGroup.copy(
                            key = subscription.globalGroups.maxOf { it.key } + 1,
                        )
                    } else {
                        newGroup
                    }
                    subscription.copy(
                        globalGroups = subscription.globalGroups + normalizedGroup,
                    )
                }
            }
        }
        if (isEdit) {
            toast("更新成功")
        } else {
            toast("添加成功")
        }
    }

    init {
        addCloseable { clearJson5TransformationCache() }
    }
}

private fun checkGroupKeyName(
    groups: List<RawSubscription.RawGroupProps>,
    newGroup: RawSubscription.RawGroupProps
) {
    if (groups.any { it.name == newGroup.name }) {
        error("已存在同名「${newGroup.name}」规则")
    }
}
