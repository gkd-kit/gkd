package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import li.songe.gkd.MainActivity
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription
import li.songe.json5.Json5


@Composable
fun EditOrAddRuleGroupDialog(
    subs: RawSubscription,
    group: RawSubscription.RawGroupProps?,
    app: RawSubscription.RawApp?,
    addAppRule: Boolean,
    onDismissRequest: () -> Unit,
) {
    val context = LocalActivity.current as MainActivity
    var value by remember {
        mutableStateOf(group?.cacheStr ?: "")
    }
    val oldValue = remember { value }

    val updateText = context.mainVm.viewModelScope.launchAsFn(Dispatchers.Default) {
        if (oldValue.isNotEmpty() && oldValue == value) {
            toast("规则组无变动")
            onDismissRequest()
            return@launchAsFn
        }
        val element = try {
            Json5.parseToJson5Element(value).jsonObject
        } catch (e: Exception) {
            LogUtils.d(e)
            error("非法JSON:${e.message}")
        }
        if (group != null) {
            // edit mode
            val newGroup = try {
                if (app != null) {
                    if (element["groups"] is JsonArray) {
                        val id = element["id"] as? JsonPrimitive
                        if (id != null && (!id.isString || id.content != app.id)) {
                            error("id与当前应用不一致")
                        }
                        RawSubscription.parseApp(element).let { newApp ->
                            if (newApp.groups.isEmpty()) {
                                error("至少输入一个规则组")
                            }
                            newApp.groups.first()
                        }
                    } else {
                        null
                    } ?: RawSubscription.parseAppGroup(element)
                } else {
                    RawSubscription.parseGlobalGroup(element)
                }
            } catch (e: Exception) {
                LogUtils.d(e)
                error("非法规则:${e.message}")
            }
            newGroup.errorDesc?.let { error(it) }
            if (newGroup.key != group.key) {
                error("不能更改规则组的key")
            }
            onDismissRequest()
            val newSubs = if (app != null) {
                newGroup as RawSubscription.RawAppGroup
                subs.copy(apps = subs.apps.toMutableList().apply {
                    set(
                        indexOfFirst { a -> a.id == app.id },
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
            // add mode
            if (app != null) {
                val newGroups = try {
                    if (element["groups"] is JsonArray) {
                        val id = element["id"] ?: error("缺少id")
                        if (!(id is JsonPrimitive && id.isString && id.content != app.id)) {
                            error("id与当前应用不一致")
                        }
                        RawSubscription.parseApp(element).apply {
                            if (groups.isEmpty()) {
                                error("至少输入一个规则组")
                            }
                        }.groups
                    } else {
                        null
                    } ?: listOf(RawSubscription.parseAppGroup(element))
                } catch (e: Exception) {
                    LogUtils.d(e)
                    error("非法规则:${e.message}")
                }
                newGroups.forEach { g ->
                    checkGroupKeyName(app.groups, g)
                    g.errorDesc?.let { error(it) }
                }
                onDismissRequest()
                updateSubscription(subs.copy(apps = subs.apps.toMutableList().apply {
                    val i = indexOfFirst { a -> a.id == app.id }
                    if (i >= 0) {
                        set(
                            i,
                            app.copy(groups = app.groups + newGroups)
                        )
                    } else {
                        add(app.copy(groups = app.groups + newGroups))
                    }
                }))
            } else {
                if (addAppRule) {
                    val newApp = try {
                        RawSubscription.parseApp(element).apply {
                            if (groups.isEmpty()) {
                                error("至少输入一个规则组")
                            }
                        }
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        error("非法规则:${e.message}")
                    }
                    onDismissRequest()
                    updateSubscription(subs.copy(apps = subs.apps.toMutableList().apply {
                        val app = find { it.id == newApp.id }
                        if (app != null) {
                            newApp.groups.forEach { g ->
                                checkGroupKeyName(app.groups, g)
                            }
                            add(newApp.copy(groups = newApp.groups + newApp.groups))
                        } else {
                            add(newApp)
                        }
                    }))
                } else {
                    val newGroup = try {
                        RawSubscription.parseGlobalGroup(element)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        error("非法规则:${e.message}")
                    }
                    checkGroupKeyName(subs.globalGroups, newGroup)
                    onDismissRequest()
                    updateSubscription(
                        subs.copy(
                            globalGroups = subs.globalGroups + newGroup
                        )
                    )
                }
            }
        }
        if (group != null) {
            toast("更新成功")
        } else {
            toast("添加成功")
        }
    }

    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(text = if (group != null) "编辑规则组" else "新增规则组") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.autoFocus(),
                placeholder = {
                    Text(
                        text = if (app != null || addAppRule) {
                            "请输入应用规则组\n"
                        } else {
                            "请输入全局规则组\n"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                minLines = 8,
                maxLines = 12,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        },
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = throttle(onDismissRequest)) {
                Text(text = "取消")
            }
        },
        confirmButton = {
            TextButton(
                onClick = throttle(updateText),
                enabled = value.isNotEmpty()
            ) {
                Text(text = if (group != null) "更新" else "添加")
            }
        },
    )
}

private fun checkGroupKeyName(
    groups: List<RawSubscription.RawGroupProps>,
    newGroup: RawSubscription.RawGroupProps
) {
    if (groups.any { it.name == newGroup.name }) {
        error("已存在同名[${newGroup.name}]规则组")
    }
    if (groups.any { it.key == newGroup.key }) {
        error("已存在同 key=${newGroup.key} 规则组")
    }
}
