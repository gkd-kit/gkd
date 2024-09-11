package li.songe.gkd

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ServiceUtils
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.permission.AuthDialog
import li.songe.gkd.permission.updatePermissionState
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.service.ManageService
import li.songe.gkd.service.fixRestartService
import li.songe.gkd.service.updateLauncherAppId
import li.songe.gkd.ui.NavGraphs
import li.songe.gkd.ui.component.BuildDialog
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.UpgradeDialog
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.initFolder
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.openApp
import li.songe.gkd.util.openUri
import li.songe.gkd.util.storeFlow

class MainActivity : ComponentActivity() {
    val mainVm by viewModels<MainViewModel>()
    val launcher by lazy { StartActivityLauncher(this) }
    val pickContentLauncher by lazy { PickContentLauncher(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        fixTopPadding()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            storeFlow.map(lifecycleScope) { s -> s.excludeFromRecents }.collect {
                (app.getSystemService(ACTIVITY_SERVICE) as ActivityManager).let { manager ->
                    manager.appTasks.forEach { task ->
                        task?.setExcludeFromRecents(it)
                    }
                }
            }
        }

        mainVm
        launcher
        pickContentLauncher
        ManageService.autoStart(this)

        setContent {
            val navController = rememberNavController()
            AppTheme {
                CompositionLocalProvider(
                    LocalNavController provides navController
                ) {
                    DestinationsNavHost(
                        navGraph = NavGraphs.root,
                        navController = navController
                    )
                    ShizukuErrorDialog(mainVm.shizukuErrorFlow)
                    AuthDialog(mainVm.authReasonFlow)
                    BuildDialog(mainVm.dialogFlow)
                    if (META.updateEnabled) {
                        UpgradeDialog(mainVm.updateStatus)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 每次切换页面更新记录桌面 appId
        appScope.launchTry(Dispatchers.IO) {
            updateLauncherAppId()
        }

        // 在某些机型由于未知原因创建失败, 在此保证每次界面切换都能重新检测创建
        appScope.launchTry(Dispatchers.IO) {
            initFolder()
        }

        // 用户在系统权限设置中切换权限后再切换回应用时能及时更新状态
        appScope.launchTry(Dispatchers.IO) {
            updatePermissionState()
        }

        // 由于某些机型的进程存在 安装缓存/崩溃缓存 导致服务状态可能不正确, 在此保证每次界面切换都能重新刷新状态
        appScope.launchTry(Dispatchers.IO) {
            updateServiceRunning()
        }
    }

    override fun onStart() {
        super.onStart()
        activityVisibleFlow.update { it + 1 }
    }

    override fun onStop() {
        super.onStop()
        activityVisibleFlow.update { it - 1 }
    }

    private var lastBackPressedTime = 0L

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // onBackPressedDispatcher.addCallback is not work, it will be covered by compose navigation
        val t = System.currentTimeMillis()
        if (t - lastBackPressedTime > AnimationConstants.DefaultDurationMillis) {
            lastBackPressedTime = t
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

private val activityVisibleFlow by lazy { MutableStateFlow(0) }
fun isActivityVisible() = activityVisibleFlow.value > 0

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


private fun updateServiceRunning() {
    ManageService.isRunning.value = ServiceUtils.isServiceRunning(ManageService::class.java)
    GkdAbService.isRunning.value = ServiceUtils.isServiceRunning(GkdAbService::class.java)
    FloatingService.isRunning.value = ServiceUtils.isServiceRunning(FloatingService::class.java)
    ScreenshotService.isRunning.value = ServiceUtils.isServiceRunning(ScreenshotService::class.java)
    HttpService.isRunning.value = ServiceUtils.isServiceRunning(HttpService::class.java)
    fixRestartService()
}

private fun Activity.fixTopPadding() {
    // 当调用系统分享时, 会导致状态栏区域消失, 应用整体上移, 设置一个 top padding 保证不上移
    var tempTop: Int? = null
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
        view.setBackgroundColor(Color.TRANSPARENT)
        val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
        if (statusBars.top == 0) {
            view.setPadding(
                statusBars.left,
                tempTop ?: BarUtils.getStatusBarHeight(),
                statusBars.right,
                statusBars.bottom
            )
        } else {
            tempTop = statusBars.top
            view.setPadding(statusBars.left, 0, statusBars.right, statusBars.bottom)
        }
        ViewCompat.onApplyWindowInsets(view, windowInsets)
    }
}

@Composable
private fun ShizukuErrorDialog(stateFlow: MutableStateFlow<Boolean>) {
    val state = stateFlow.collectAsState()
    if (state.value) {
        val appId = "moe.shizuku.privileged.api"
        val appInfoCache = appInfoCacheFlow.collectAsState()
        val installed = appInfoCache.value.contains(appId)
        AlertDialog(
            onDismissRequest = { stateFlow.value = false },
            title = { Text(text = "授权错误") },
            text = {
                Text(
                    text = if (installed) {
                        "Shizuku 授权失败, 请检查是否运行"
                    } else {
                        "Shizuku 未安装, 请先下载后安装"
                    }
                )
            },
            confirmButton = {
                if (installed) {
                    TextButton(onClick = {
                        stateFlow.value = false
                        app.openApp(appId)
                    }) {
                        Text(text = "打开 Shizuku")
                    }
                } else {
                    TextButton(onClick = {
                        stateFlow.value = false
                        app.openUri("https://shizuku.rikka.app/")
                    }) {
                        Text(text = "去下载")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { stateFlow.value = false }) {
                    Text(text = "我知道了")
                }
            }
        )
    }
}
