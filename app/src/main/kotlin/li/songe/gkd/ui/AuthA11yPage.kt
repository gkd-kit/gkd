package li.songe.gkd.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.service.fixRestartService
import li.songe.gkd.shizuku.newPackageManager
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.openA11ySettings
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import java.io.DataOutputStream

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AuthA11yPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<AuthA11yVm>()
    val showCopyDlg by vm.showCopyDlgFlow.collectAsState()
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()
    var mode by remember { mutableIntStateOf(-1) }

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
            Text(text = "授权说明")
        }, actions = {})
    }) { contentPadding ->
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            if (writeSecureSettings) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "授权成功,请关闭此页面",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "选择一个授权模式",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
                Card(
                    modifier = Modifier
                        .padding(16.dp, 0.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mode == 0) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Unspecified
                        }
                    ),
                    onClick = { mode = 0 }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == 0,
                            onClick = { mode = 0 }
                        )
                        Text(
                            text = "普通授权(简单)",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        modifier = Modifier.padding(16.dp, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = "1. 授予[无障碍权限]"
                    )
                    Text(
                        modifier = Modifier.padding(16.dp, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = "2. 无障碍服务关闭后需重新授权"
                    )
                    Row(
                        modifier = Modifier
                            .padding(16.dp, 0.dp)
                            .fillMaxWidth(),
                    ) {
                        TextButton(onClick = throttle { openA11ySettings() }) {
                            Text(
                                text = "手动授权",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .padding(16.dp, 0.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mode == 1) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Unspecified
                        }
                    ),
                    onClick = { mode = 1 }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == 1,
                            onClick = { mode = 1 }
                        )
                        Text(text = "高级授权(推荐)")
                    }
                    Text(
                        modifier = Modifier.padding(16.dp, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = "1. 授予[写入安全设置权限]"
                    )
                    Text(
                        modifier = Modifier.padding(16.dp, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = "2. 授权永久有效, 可自动重启无障碍服务"
                    )
                    Text(
                        modifier = Modifier.padding(16.dp, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = "3. 搭配通知栏快捷图标可实现无感重启, 无限保活"
                    )
                    Row(
                        modifier = Modifier
                            .padding(16.dp, 0.dp)
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
                        TextButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            grantPermissionByRoot()
                        })) {
                            Text(
                                text = "ROOT授权",
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
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
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
                    Text(
                        text = commandText,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.showCopyDlgFlow.value = false
                    ClipboardUtils.copyText(commandText)
                    toast("复制成功")
                }) {
                    Text(text = "复制并关闭")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showCopyDlgFlow.value = false }) {
                    Text(text = "关闭")
                }
            }
        )
    }
}

private val commandText by lazy { "adb pm grant ${META.appId} android.permission.WRITE_SECURE_SETTINGS" }

private suspend fun MainActivity.grantPermissionByShizuku() {
    if (shizukuOkState.stateFlow.value) {
        try {
            val service = newPackageManager()
            if (service != null) {
                service.grantRuntimePermission(
                    META.appId,
                    "android.permission.WRITE_SECURE_SETTINGS",
                    0, // maybe others
                )
                delay(500)
                if (writeSecureSettingsState.updateAndGet()) {
                    toast("授权成功")
                    fixRestartService()
                }
            }
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

private fun grantPermissionByRoot() {
    var p: Process? = null
    try {
        p = Runtime.getRuntime().exec("su")
        val o = DataOutputStream(p.outputStream)
        o.writeBytes("pm grant ${META.appId} android.permission.WRITE_SECURE_SETTINGS\nexit\n")
        o.flush()
        o.close()
        p.waitFor()
        if (p.exitValue() == 0) {
            toast("授权成功")
        }
    } catch (e: Exception) {
        toast("授权失败:${e.message}")
        LogUtils.d(e)
    } finally {
        p?.destroy()
    }
}
