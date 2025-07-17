package li.songe.gkd.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.WebViewPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.META
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.fixRestartService
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AuthButtonGroup
import li.songe.gkd.ui.component.ManualAuthDialog
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.local.LocalMainViewModel
import li.songe.gkd.ui.local.LocalNavController
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.cardHorizontalPadding
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.surfaceCardColors
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.openA11ySettings
import li.songe.gkd.util.runCommandByRoot
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

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
        })
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
                                mainVm.navigatePage(WebViewPageDestination(initUrl = (ShortUrlSet.URL2)))
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
                    text = "1. 授予「写入安全设置权限」\n2. 授权永久有效, 包含「无障碍权限」\n3. 应用可自行控制开关无障碍服务\n4. 在通知栏快捷开关可快捷重启, 无感保活"
                )
                if (!writeSecureSettings) {
                    A11yAuthButtonGroup()
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
                    A11yAuthButtonGroup()
                    Text(
                        modifier = Modifier
                            .padding(cardHorizontalPadding, 0.dp)
                            .clickable(onClick = throttle {
                                mainVm.navigatePage(WebViewPageDestination(initUrl = ShortUrlSet.URL2))
                            }),
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

    ManualAuthDialog(
        commandText = a11yCommandText,
        show = showCopyDlg,
        onUpdateShow = {
            vm.showCopyDlgFlow.value = it
        },
    )
}

private val a11yCommandText by lazy {
    arrayOf(
        "pm grant ${META.appId} android.permission.WRITE_SECURE_SETTINGS",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "appops set ${META.appId} ACCESS_RESTRICTED_SETTINGS allow"
        } else {
            null
        },
    ).filterNotNull().joinToString("; ").trimEnd()
}

private fun successAuthExec() {
    if (writeSecureSettingsState.updateAndGet()) {
        toast("授权成功")
        storeFlow.update { it.copy(enableService = true) }
        fixRestartService()
    }
}

@Composable
private fun A11yAuthButtonGroup() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AuthA11yVm>()
    AuthButtonGroup(
        onClickShizuku = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            mainVm.grantPermissionByShizuku(a11yCommandText)
            successAuthExec()
        },
        onClickManual = {
            vm.showCopyDlgFlow.value = true
        },
        onClickRoot = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            runCommandByRoot(a11yCommandText)
            toast("授权成功")
        }
    )
}