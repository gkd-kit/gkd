package li.songe.gkd.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.fixRestartService
import li.songe.gkd.shizuku.execCommandForResult
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.surfaceCardColors
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.copyText
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.openA11ySettings
import li.songe.gkd.util.openUri
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import java.io.DataOutputStream

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AuthA11yPage() {
    val mainVm = LocalMainViewModel.current
    val navController = LocalNavController.current

    val vm = viewModel<AuthA11yVm>()
    val showCopyDlg by vm.showCopyDlgFlow.collectAsState()
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()
    val a11yRunning by A11yService.isRunning.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = {
            Text(text = "授权状态")
        }, actions = {})
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            Text(
                text = "选择一个授权模式进行操作",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(itemHorizontalPadding)
            )
            Card(
                modifier = Modifier
                    .padding(itemHorizontalPadding, 0.dp)
                    .fillMaxWidth(),
                onClick = { },
                colors = surfaceCardColors,
            ) {
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 8.dp),
                    text = "普通授权(简单)",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    text = "1. 授予「无障碍权限」\n2. 无障碍服务关闭后需重新授权"
                )
                if (writeSecureSettings || a11yRunning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = "已持有「无障碍权限」, 可继续使用",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .padding(4.dp, 0.dp)
                            .fillMaxWidth(),
                    ) {
                        TextButton(onClick = throttle { openA11ySettings() }) {
                            Text(
                                text = "手动授权",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Text(
                        modifier = Modifier
                            .padding(cardHorizontalPadding, 0.dp)
                            .clickable {
                                openUri(ShortUrlSet.URL2)
                            },
                        text = "无法开启无障碍?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .padding(itemHorizontalPadding, 0.dp)
                    .fillMaxWidth(),
                onClick = { },
                colors = surfaceCardColors,
            ) {
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 8.dp),
                    text = "高级授权(推荐)",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    text = "1. 授予「写入安全设置权限」\n2. 授权永久有效, 包含「无障碍权限」\n3. 应用重启后可自动打开无障碍服务\n4. 在通知栏快捷开关可快捷重启, 无感保活"
                )
                if (!writeSecureSettings) {
                    AuthButtonGroup()
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = "已持有「写入安全设置权限」 优先使用此权限",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(4.dp, 0.dp)
                        .fillMaxWidth(),
                ) {
                    TextButton(onClick = throttle {
                        if (!writeSecureSettings) {
                            toast("请先授权")
                        }
                        mainVm.dialogFlow.updateDialogOptions(
                            title = "无感保活",
                            text = "添加通知栏快捷开关\n\n1. 下拉通知栏至「快捷开关」标界面\n2. 找到名称为 ${META.appName} 的快捷开关\n3. 添加此开关到通知面板 \n\n只要此快捷开关在通知面板可见\n无论是系统杀后台还是自身BUG崩溃\n简单下拉打开通知即可重启"
                        )
                    }) {
                        Text(
                            text = "无感保活",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (writeSecureSettings) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .padding(itemHorizontalPadding, 0.dp)
                        .fillMaxWidth(),
                    onClick = { },
                    colors = surfaceCardColors,
                ) {
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 8.dp),
                        text = "解除可能受到的无障碍限制",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = "1. 某些系统有更严格的无障碍限制\n2. 在 GKD 更新后会限制其开关无障碍\n3. 重新授权可解决此问题"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = "若能正常开关无障碍请忽略此项",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AuthButtonGroup()
                    Text(
                        modifier = Modifier
                            .padding(cardHorizontalPadding, 0.dp)
                            .clickable {
                                openUri(ShortUrlSet.URL2)
                            },
                        text = "其他方式解除限制",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

    if (showCopyDlg) {
        AlertDialog(
            onDismissRequest = { vm.showCopyDlgFlow.value = false },
            title = { Text(text = "手动授权") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "1. 有一台安装了 adb 的电脑\n\n2.手机开启调试模式后连接电脑授权调试\n\n3. 在电脑 cmd/pwsh 中运行如下命令")
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = commandText,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable(onClick = throttle {
                                    copyText(commandText)
                                })
                                .padding(4.dp)
                                .size(16.dp),
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        modifier = Modifier
                            .clickable {
                                openUri(ShortUrlSet.URL3)
                            },
                        text = "运行后授权失败?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.showCopyDlgFlow.value = false
                }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}

private val innerCommandText by lazy {
    arrayOf(
        "appops set ${META.appId} ACCESS_RESTRICTED_SETTINGS allow",
        "pm grant ${META.appId} android.permission.WRITE_SECURE_SETTINGS"
    ).joinToString("; ")
}
private val commandText by lazy { "adb shell \"${innerCommandText}\"" }

private fun successAuthExec() {
    if (writeSecureSettingsState.updateAndGet()) {
        toast("授权成功")
        storeFlow.update { it.copy(enableService = true) }
        fixRestartService()
    }
}

private suspend fun MainActivity.grantPermissionByShizuku() {
    if (shizukuOkState.stateFlow.value) {
        try {
            execCommandForResult(innerCommandText)
            successAuthExec()
        } catch (e: Exception) {
            toast("授权失败:${e.message}")
            LogUtils.d(e)
        }
    } else {
        try {
            Shizuku.requestPermission(Activity.RESULT_OK)
        } catch (e: Exception) {
            LogUtils.d("Shizuku授权错误", e.message)
            mainVm.shizukuErrorFlow.value = true
        }
    }
}

private val cardHorizontalPadding = 12.dp

private fun grantPermissionByRoot() {
    var p: Process? = null
    try {
        p = Runtime.getRuntime().exec("su")
        val o = DataOutputStream(p.outputStream)
        o.writeBytes("${innerCommandText}\nexit\n")
        o.flush()
        o.close()
        p.waitFor()
        if (p.exitValue() == 0) {
            successAuthExec()
        }
    } catch (e: Exception) {
        toast("授权失败:${e.message}")
        LogUtils.d(e)
    } finally {
        p?.destroy()
    }
}


@Composable
private fun AuthButtonGroup() {
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<AuthA11yVm>()
    Row(
        modifier = Modifier
            .padding(4.dp, 0.dp)
            .fillMaxWidth(),
    ) {
        TextButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            context.grantPermissionByShizuku()
        })) {
            Text(
                text = "Shizuku授权",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        TextButton(onClick = {
            vm.showCopyDlgFlow.value = true
        }) {
            Text(
                text = "手动授权",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        TextButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            grantPermissionByRoot()
        })) {
            Text(
                text = "ROOT授权",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}