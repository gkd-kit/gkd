package li.songe.gkd

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.blankj.utilcode.util.ServiceUtils
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.composition.CompositionActivity
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.permission.AuthDialog
import li.songe.gkd.permission.updatePermissionState
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.service.ManageService
import li.songe.gkd.service.updateLauncherAppId
import li.songe.gkd.ui.NavGraphs
import li.songe.gkd.ui.component.ConfirmDialog
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.LocalPickContentLauncher
import li.songe.gkd.util.UpgradeDialog
import li.songe.gkd.util.initFolder
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow

@AndroidEntryPoint
class MainActivity : CompositionActivity({
    this as MainActivity
    useLifeCycleLog()
    val launcher = StartActivityLauncher(this)
    val pickContentLauncher = PickContentLauncher(this)

    lifecycleScope.launch {
        storeFlow.map(lifecycleScope) { s -> s.excludeFromRecents }.collect {
            (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let { manager ->
                manager.appTasks.forEach { task ->
                    task?.setExcludeFromRecents(it)
                }
            }
        }
    }

    ManageService.autoStart(this)
    mainVm

    setContent {
        val navController = rememberNavController()
        AppTheme {
            CompositionLocalProvider(
                LocalLauncher provides launcher,
                LocalPickContentLauncher provides pickContentLauncher,
                LocalNavController provides navController
            ) {
                DestinationsNavHost(
                    navGraph = NavGraphs.root,
                    navController = navController
                )
            }
            ConfirmDialog()
            AuthDialog()
            UpgradeDialog()
        }
    }
}) {
    val mainVm by viewModels<MainViewModel>()

    override fun onStart() {
        super.onStart()
        activityVisibleFlow.update { it + 1 }
    }

    override fun onResume() {
        super.onResume()

        // 每次切换页面更新记录桌面 appId
        updateLauncherAppId()

        // 在某些机型由于未知原因创建失败, 在此保证每次界面切换都能重新检测创建
        appScope.launch(Dispatchers.IO) {
            initFolder()
        }

        updatePermissionState()

        // 进程崩溃后重新打开应用, 由于存在缓存导致服务状态可能不正确, 在此保证每次界面切换都能重新刷新状态
        appScope.launch(Dispatchers.IO) {
            updateServiceRunning()
        }
    }

    override fun onStop() {
        super.onStop()
        activityVisibleFlow.update { it - 1 }
    }
}

val activityVisibleFlow = MutableStateFlow(0)

fun Activity.navToMainActivity() {
    val intent = this.intent?.cloneFilter()
    if (intent != null) {
        intent.component = ComponentName(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("source", this::class.qualifiedName)
        startActivity(intent)
    }
    finish()
}

fun updateServiceRunning() {
    ManageService.isRunning.value = ServiceUtils.isServiceRunning(ManageService::class.java)
    GkdAbService.isRunning.value = ServiceUtils.isServiceRunning(GkdAbService::class.java)
    FloatingService.isRunning.value = ServiceUtils.isServiceRunning(FloatingService::class.java)
    ScreenshotService.isRunning.value = ServiceUtils.isServiceRunning(ScreenshotService::class.java)
    HttpService.isRunning.value = ServiceUtils.isServiceRunning(HttpService::class.java)
}
