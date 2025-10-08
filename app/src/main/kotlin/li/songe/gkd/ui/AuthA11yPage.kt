package li.songe.gkd.ui

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.META
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.fixRestartService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AuthButtonGroup
import li.songe.gkd.ui.component.ManualAuthDialog
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.cardHorizontalPadding
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.surfaceCardColors
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.openA11ySettings
import li.songe.gkd.util.shFolder
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AuthA11yPage() {
    val mainVm = LocalMainViewModel.current

    val vm = viewModel<AuthA11yVm>()
    val showCopyDlg by vm.showCopyDlgFlow.collectAsState()
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()
    val a11yRunning by A11yService.isRunning.collectAsState()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                mainVm.popBackStack()
            })
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
                    style = MaterialTheme.typography.titleMedium,
                )
                TextListItem(
                    modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "授予「无障碍权限」",
                        "无障碍关闭后需重新授权"
                    ),
                )
                if (writeSecureSettings || a11yRunning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = "已持有「无障碍权限」，可继续使用",
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
                                mainVm.navigateWebPage(ShortUrlSet.URL2)
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
                    style = MaterialTheme.typography.titleMedium,
                )
                TextListItem(
                    modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "授予「写入安全设置权限」",
                        "授权永久有效，包含「无障碍权限」",
                        "应用可自行控制开关无障碍",
                        "在通知栏快捷开关可快捷重启，无感保活"
                    ),
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
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedVisibility(writeSecureSettings && !shizukuContextFlow.collectAsState().value.ok) {
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
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextListItem(
                        list = listOf(
                            "某些系统有更严格的无障碍限制",
                            "在 GKD 更新后会限制其开关无障碍",
                            "重新授权可解决此问题",
                        ),
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = "若能正常开关无障碍请忽略此项",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    A11yAuthButtonGroup()
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

    ManualAuthDialog(
        commandText = gkdStartCommandText,
        show = showCopyDlg,
        onUpdateShow = {
            vm.showCopyDlgFlow.value = it
        },
    )
}

private val String.appopsAllow get() = "appops set ${META.appId} $this allow"
private val String.pmGrant get() = "pm grant ${META.appId} $this"

val gkdStartCommandText by lazy {
    val commandText = listOfNotNull(
        "set -euo pipefail",
        Manifest.permission.WRITE_SECURE_SETTINGS.pmGrant,
        if (AndroidTarget.TIRAMISU) "ACCESS_RESTRICTED_SETTINGS".appopsAllow else null,
        if (AndroidTarget.UPSIDE_DOWN_CAKE) "FOREGROUND_SERVICE_SPECIAL_USE".appopsAllow else null,
        "echo 'Execution Successful'",
    ).joinToString("\n")
    val file = shFolder.resolve("start.sh")
    file.writeText(commandText)
    "adb shell sh ${file.absolutePath}"
}

@Composable
private fun A11yAuthButtonGroup() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AuthA11yVm>()
    AuthButtonGroup(
        buttons = listOf(
            "手动解除" to {
                mainVm.navigateWebPage(ShortUrlSet.URL2)
            },
            "Shizuku 授权" to vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                mainVm.guardShizukuContext()
                if (writeSecureSettingsState.value) {
                    toast("授权成功")
                    storeFlow.update { it.copy(enableService = true) }
                    fixRestartService()
                }
            },
            "外部授权" to {
                vm.showCopyDlgFlow.value = true
            },
        )
    )
}

@Composable
private fun TextListItem(
    list: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val lineHeightDp = LocalDensity.current.run { style.lineHeight.toDp() }
    Column(
        modifier = modifier,
    ) {
        list.forEach { text ->
            Row {
                Spacer(
                    modifier = Modifier
                        .padding(vertical = (lineHeightDp - 4.dp) / 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .size(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = style)
            }
        }
    }
}
