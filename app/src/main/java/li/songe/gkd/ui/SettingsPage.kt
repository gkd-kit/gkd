package li.songe.gkd.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blankj.utilcode.util.ToastUtils
import com.ramcosta.composedestinations.navigation.navigate
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.AboutPageDestination
import li.songe.gkd.ui.destinations.DebugPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.checkUpdate
import li.songe.gkd.util.checkUpdatingFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStorage

val settingsNav = BottomNavItem(
    label = "设置", icon = SafeR.ic_cog, route = "settings"
)

@Composable
fun SettingsPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()

    var showSubsIntervalDlg by remember {
        mutableStateOf(false)
    }
    var showToastInputDlg by remember {
        mutableStateOf(false)
    }
    val checkUpdating by checkUpdatingFlow.collectAsState()

    Scaffold(topBar = {
        TopAppBar(backgroundColor = Color(0xfff8f9f9), title = {
            Text(
                text = "设置", color = Color.Black
            )
        })
    }, content = { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(
                    state = rememberScrollState()
                )
                .padding(0.dp, 10.dp)
                .padding(contentPadding)
        ) {

            TextSwitch(name = "后台隐藏",
                desc = "在[最近任务]界面中隐藏本应用",
                checked = store.excludeFromRecents,
                onCheckedChange = {
                    updateStorage(
                        storeFlow, store.copy(
                            excludeFromRecents = it
                        )
                    )
                })
            Divider()

            TextSwitch(name = "点击提示",
                desc = "触发点击时提示:[${store.clickToast}]",
                checked = store.toastWhenClick,
                modifier = Modifier.clickable {
                    showToastInputDlg = true
                },
                onCheckedChange = {
                    updateStorage(
                        storeFlow, store.copy(
                            toastWhenClick = it
                        )
                    )
                })
            Divider()

            Row(modifier = Modifier
                .clickable {
                    showSubsIntervalDlg = true
                }
                .padding(10.dp, 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f), text = "自动更新订阅", fontSize = 18.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = radioOptions.find { it.second == store.updateSubsInterval }?.first
                            ?: store.updateSubsInterval.toString(), fontSize = 14.sp
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight, contentDescription = "more"
                    )
                }
            }

            Divider()
            TextSwitch(name = "自动更新应用",
                desc = "打开应用时自动检测是否存在新版本",
                checked = store.autoCheckAppUpdate,
                onCheckedChange = {
                    updateStorage(
                        storeFlow, store.copy(
                            autoCheckAppUpdate = it
                        )
                    )
                })
            Divider()

            SettingItem(title = if (checkUpdating) "检查更新ing" else "检查更新", onClick = {
                appScope.launchTry {
                    if (checkUpdatingFlow.value) return@launchTry
                    val newVersion = checkUpdate()
                    if (newVersion == null) {
                        ToastUtils.showShort("暂无更新")
                    }
                }
            })
            Divider()
            SettingItem(title = "问题反馈", onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW, Uri.parse("https://github.com/gkd-kit/gkd")
                    )
                )
            })
            Divider()
            SettingItem(title = "高级模式", onClick = {
                navController.navigate(DebugPageDestination)
            })
            Divider()

            SettingItem(title = "关于", onClick = {
                navController.navigate(AboutPageDestination)
            })

            Spacer(modifier = Modifier.height(40.dp))
        }
    })

    if (showSubsIntervalDlg) {
        Dialog(onDismissRequest = { showSubsIntervalDlg = false }) {
            Column(
                modifier = Modifier
            ) {
                radioOptions.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (option.second == store.updateSubsInterval),
                                onClick = {
                                    updateStorage(
                                        storeFlow,
                                        storeFlow.value.copy(updateSubsInterval = option.second)
                                    )
                                })
                            .padding(horizontal = 16.dp)
                    ) {
                        RadioButton(selected = (option.second == store.updateSubsInterval),
                            onClick = {
                                updateStorage(
                                    storeFlow,
                                    storeFlow.value.copy(updateSubsInterval = option.second)
                                )
                            })
                        Text(
                            text = option.first,
                            style = MaterialTheme.typography.body1.merge(),
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
    if (showToastInputDlg) {
        Dialog(onDismissRequest = { showToastInputDlg = false }) {
            var value by remember {
                mutableStateOf(store.clickToast)
            }
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(text = "请输入提示文字", style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier,
                )
                Row(
                    horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { showToastInputDlg = false }) {
                        Text(
                            text = "取消", modifier = Modifier
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    TextButton(onClick = {
                        updateStorage(
                            storeFlow, store.copy(
                                clickToast = value
                            )
                        )
                        showToastInputDlg = false
                    }) {
                        Text(
                            text = "确认", modifier = Modifier
                        )
                    }
                }
            }
        }
    }
}

val radioOptions = listOf(
    "暂停" to -1L,
    "每小时" to 60 * 60_000L,
    "每12小时" to 12 * 60 * 60_000L,
    "每天" to 24 * 60 * 60_000L
)
