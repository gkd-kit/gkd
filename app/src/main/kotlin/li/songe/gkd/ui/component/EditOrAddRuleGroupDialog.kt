package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
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
        val newGroup = try {
            if (app != null) {
                if (element["groups"] is JsonArray) {
                    // 额外支持直接编辑 app 的 groups
                    RawSubscription.parseApp(element).groups.let {
                        it.find { g -> g.key == group?.key } ?: it.firstOrNull()
                    }
                } else {
                    null
                } ?: RawSubscription.parseGroup(element)
            } else {
                RawSubscription.parseRawGlobalGroup(value)
            }
        } catch (e: Exception) {
            LogUtils.d(e)
            error("非法规则:${e.message}")
        }
        if (newGroup.errorDesc != null) {
            error(newGroup.errorDesc!!)
        }
        if (group != null) {
            if (newGroup.key != group.key) {
                error("不能更改规则组的key")
            }
        } else {
            if (app != null) {
                if (app.groups.any { it.name == newGroup.name }) {
                    error("已存在同名[${newGroup.name}]规则组")
                }
                if (app.groups.any { it.key == newGroup.key }) {
                    error("已存在同 key=${newGroup.key} 规则组")
                }
            } else {
                if (subs.globalGroups.any { it.name == newGroup.name }) {
                    error("已存在同名[${newGroup.name}]规则组")
                }
                if (subs.globalGroups.any { it.key == newGroup.key }) {
                    error("已存在同 key=${newGroup.key} 规则组")
                }
            }
        }
        onDismissRequest()
        val newSubs = if (app != null) {
            newGroup as RawSubscription.RawAppGroup
            subs.copy(apps = subs.apps.toMutableList().apply {
                if (group != null) {
                    set(
                        indexOfFirst { a -> a.id == app.id },
                        app.copy(groups = app.groups.toMutableList().apply {
                            set(
                                indexOfFirst { g -> g.key == newGroup.key },
                                newGroup
                            )
                        })
                    )
                } else {
                    if (all { it.id != app.id }) {
                        add(app.copy(groups = mutableListOf(newGroup)))
                    } else {
                        set(
                            indexOfFirst { a -> a.id == app.id },
                            app.copy(groups = app.groups.toMutableList().apply {
                                add(newGroup)
                            })
                        )
                    }
                }
            })
        } else {
            newGroup as RawSubscription.RawGlobalGroup
            val newGlobalGroups = subs.globalGroups.toMutableList().apply {
                val i = indexOfFirst { g -> g.key == newGroup.key }
                if (i >= 0) {
                    set(i, newGroup)
                } else {
                    add(newGroup)
                }
            }
            subs.copy(globalGroups = newGlobalGroups)
        }
        updateSubscription(newSubs)
        if (group != null) {
            toast("更新成功")
        } else {
            toast("添加成功")
        }
    }

    val focusRequester = remember { FocusRequester() }
    val inputFocused = rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        title = { Text(text = if (group != null) "编辑规则组" else "新增规则组") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            inputFocused.value = true
                        }
                    },
                placeholder = {
                    Text(
                        text = if (app != null) "请输入规则组\n可以是APP规则\n也可以是单个规则组" else "请输入全局规则组",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                minLines = 8,
                maxLines = 12,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            LaunchedEffect(null) {
                focusRequester.requestFocus()
            }
        },
        onDismissRequest = {
            if (!inputFocused.value) {
                onDismissRequest()
            }
        },
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