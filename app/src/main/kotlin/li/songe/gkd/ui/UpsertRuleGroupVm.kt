package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.generated.destinations.UpsertRuleGroupPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.ui.style.clearJson5TransformationCache
import li.songe.gkd.util.subsMapFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription
import li.songe.json5.Json5

class UpsertRuleGroupVm(stateHandle: SavedStateHandle) : ViewModel() {
    val args = UpsertRuleGroupPageDestination.argsFrom(stateHandle)
    val groupKey = args.groupKey
    val appId = args.appId

    val isEdit = groupKey != null
    val isApp = appId != null
    val isAddAnyApp = appId == ""

    private val initialGroup: RawSubscription.RawGroupProps? = run {
        val subs = subsMapFlow.value[args.subsId]
        subs ?: return@run null
        if (groupKey != null) {
            if (appId != null) {
                subs.getAppGroups(appId)
            } else {
                subs.globalGroups
            }.find { it.key == args.groupKey }
        } else {
            null
        }
    }

    private val initText = initialGroup?.cacheStr ?: ""
    val textFlow = MutableStateFlow(initText)

    fun hasTextChanged(): Boolean {
        val text = textFlow.value
        if (!isEdit) return !text.isBlank()
        if (initText == text) return false
        return initialGroup?.cacheJsonObject != runCatching { Json5.parseToJson5Element(text) }.getOrNull()
    }


    var addAppId: String? = null

    fun saveRule() {
        val subs = subsMapFlow.value[args.subsId] ?: error("订阅不存在")
        val text = textFlow.value
        if (text.isBlank()) {
            error("规则不能为空")
        }
        if (text == initText) {
            toast("规则无变动")
            return
        }
        val jsonObject = runCatching { Json5.parseToJson5Element(text) }.run {
            if (isFailure) {
                error("非法格式\n${exceptionOrNull()?.message}")
            }
            getOrThrow()
        }
        if (jsonObject !is JsonObject) {
            error("规则应为对象格式")
        }
        if (jsonObject == initialGroup?.cacheJsonObject) {
            toast("规则无变动")
            return
        }
        if (groupKey != null) {
            val newGroup = try {
                if (appId != null) {
                    if (jsonObject["groups"] is JsonArray) {
                        val id = jsonObject["id"] ?: error("缺少id")
                        if (!(id is JsonPrimitive && id.isString && id.content == appId)) {
                            error("id与当前应用不一致")
                        }
                        RawSubscription.parseApp(jsonObject).let { newApp ->
                            if (newApp.groups.isEmpty()) {
                                error("至少输入一个规则组")
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
                error("不能更改规则组的key")
            }
            val newSubs = if (appId != null) {
                newGroup as RawSubscription.RawAppGroup
                val app = subs.apps.find { a -> a.id == appId } ?: error("应用不存在")
                subs.copy(apps = subs.apps.toMutableList().apply {
                    set(
                        indexOfFirst { a -> a.id == appId },
                        app.copy(groups = app.groups.toMutableList().apply {
                            set(
                                indexOfFirst { g -> g.key == newGroup.key },
                                newGroup
                            )
                        })
                    )
                })
            } else {
                newGroup as RawSubscription.RawGlobalGroup
                subs.copy(globalGroups = subs.globalGroups.toMutableList().apply {
                    set(indexOfFirst { g -> g.key == newGroup.key }, newGroup)
                })
            }
            updateSubscription(newSubs)
        } else {
            if (isAddAnyApp) {
                val newApp = try {
                    RawSubscription.parseApp(jsonObject).apply {
                        if (groups.isEmpty()) {
                            error("至少输入一个规则组")
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.d(e)
                    error("非法规则\n${e.message}")
                }
                val oldApp = subs.apps.find { it.id == newApp.id }
                if (oldApp != null) {
                    newApp.groups.forEach { g ->
                        checkGroupKeyName(oldApp.groups, g)
                    }
                }
                val newSubs = subs.copy(apps = subs.apps.toMutableList().apply {
                    val i = indexOfFirst { a -> a.id == newApp.id }
                    if (i >= 0) {
                        set(
                            i,
                            get(i).copy(groups = get(i).groups + newApp.groups),
                        )
                    } else {
                        add(newApp)
                    }
                })
                addAppId = newApp.id
                updateSubscription(newSubs)
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
                                error("至少输入一个规则组")
                            }
                        }.groups
                    } else {
                        null
                    } ?: listOf(RawSubscription.parseAppGroup(jsonObject))
                } catch (e: Exception) {
                    LogUtils.d(e)
                    error("非法规则\n${e.message}")
                }
                val oldApp = subs.getApp(appId)
                newGroups.forEach { g ->
                    checkGroupKeyName(oldApp.groups, g)
                    g.errorDesc?.let { error(it) }
                }
                val newSubs = subs.copy(apps = subs.apps.toMutableList().apply {
                    val newApp = oldApp.copy(groups = oldApp.groups + newGroups)
                    val i = indexOfFirst { a -> a.id == newApp.id }
                    if (i >= 0) {
                        set(
                            i,
                            newApp
                        )
                    } else {
                        add(newApp)
                    }
                })
                updateSubscription(newSubs)
            } else {
                // add global group
                val newGroup = try {
                    RawSubscription.parseGlobalGroup(jsonObject)
                } catch (e: Exception) {
                    LogUtils.d(e)
                    error("非法规则\n${e.message}")
                }
                checkGroupKeyName(subs.globalGroups, newGroup)
                updateSubscription(
                    subs.copy(
                        globalGroups = subs.globalGroups + newGroup
                    )
                )
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
        error("已存在同名「${newGroup.name}」规则组")
    }
    if (groups.any { it.key == newGroup.key }) {
        error("已存在同 key=${newGroup.key} 规则组")
    }
}
