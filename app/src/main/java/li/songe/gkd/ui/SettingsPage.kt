package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme.typography
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.RomUtils
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.navigation.navigate
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.AboutPageDestination
import li.songe.gkd.ui.destinations.ClickLogPageDestination
import li.songe.gkd.ui.destinations.DebugPageDestination
import li.songe.gkd.ui.destinations.SnapshotPageDestination
import li.songe.gkd.util.Ext
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.checkUpdate
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStorage
import li.songe.gkd.util.usePollState
import rikka.shizuku.Shizuku

val settingsNav = BottomNavItem(
    label = "设置", icon = SafeR.ic_cog, route = "settings"
)

@Composable
fun SettingsPage() {
    val context = LocalContext.current as MainActivity
    val launcher = LocalLauncher.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val store by storeFlow.collectAsState()

    var showPortDlg by remember {
        mutableStateOf(false)
    }

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

            Spacer(modifier = Modifier.height(5.dp))
            TextSwitch(name = "自动更新订阅",
                desc = "每隔一段时间自动更新订阅规则文件",
                checked = store.autoUpdateSubs,
                onCheckedChange = {
                    updateStorage(
                        storeFlow, store.copy(
                            autoUpdateSubs = it
                        )
                    )
                })

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

            SettingItem(title = "检查更新", onClick = {
                appScope.launchTry {
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
                        Intent.ACTION_VIEW, Uri.parse("https://github.com/gkd-kit/subscription")
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

}