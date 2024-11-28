package li.songe.gkd

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.blankj.utilcode.util.BarUtils
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.AuthA11YPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.permission.AuthDialog
import li.songe.gkd.permission.updatePermissionState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.ManageService
import li.songe.gkd.service.fixRestartService
import li.songe.gkd.service.updateLauncherAppId
import li.songe.gkd.ui.component.BuildDialog
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.EditGithubCookieDlg
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.UpgradeDialog
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.componentName
import li.songe.gkd.util.initFolder
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.openApp
import li.songe.gkd.util.openUri
import li.songe.gkd.util.storeFlow
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class MainActivity : ComponentActivity() {
    val mainVm by viewModels<MainViewModel>()
    val launcher by lazy { StartActivityLauncher(this) }
    val pickContentLauncher by lazy { PickContentLauncher(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        fixTopPadding()
        fixTransparentNavigationBar()
        super.onCreate(savedInstanceState)
        mainVm
        launcher
        pickContentLauncher
        ManageService.autoStart()
        lifecycleScope.launch {
            storeFlow.map(lifecycleScope) { s -> s.excludeFromRecents }.collect {
                (app.getSystemService(ACTIVITY_SERVICE) as ActivityManager).let { manager ->
                    manager.appTasks.forEach { task ->
                        task?.setExcludeFromRecents(it)
                    }
                }
            }
        }
        setContent {
            val navController = rememberNavController()
            AppTheme {
                CompositionLocalProvider(
                    LocalNavController provides navController
                ) {
                    DestinationsNavHost(
                        navController = navController,
                        navGraph = NavGraphs.root
                    )
                    AccessRestrictedSettingsDlg()
                    ShizukuErrorDialog(mainVm.shizukuErrorFlow)
                    AuthDialog(mainVm.authReasonFlow)
                    BuildDialog(mainVm.dialogFlow)
                    mainVm.uploadOptions.ShowDialog()
                    EditGithubCookieDlg(mainVm.showEditCookieDlgFlow)
                    if (META.updateEnabled) {
                        UpgradeDialog(mainVm.updateStatus)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncFixState()
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
        intent.component = MainActivity::class.componentName
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("source", this::class.qualifiedName)
        startActivity(intent)
    }
    finish()
}

@Suppress("DEPRECATION")
private fun updateServiceRunning() {
    val list = try {
        val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getRunningServices(Int.MAX_VALUE) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun checkRunning(cls: KClass<*>): Boolean {
        return list.any { it.service.className == cls.jvmName }
    }
    ManageService.isRunning.value = checkRunning(ManageService::class)
    A11yService.isRunning.value = checkRunning(A11yService::class)
    FloatingService.isRunning.value = checkRunning(FloatingService::class)
    ScreenshotService.isRunning.value = checkRunning(ScreenshotService::class)
    HttpService.isRunning.value = checkRunning(HttpService::class)
}

private val syncStateMutex = Mutex()
fun syncFixState() {
    appScope.launchTry(Dispatchers.IO) {
        syncStateMutex.withLock {
            // 每次切换页面更新记录桌面 appId
            updateLauncherAppId()

            // 在某些机型由于未知原因创建失败, 在此保证每次界面切换都能重新检测创建
            initFolder()

            // 由于某些机型的进程存在 安装缓存/崩溃缓存 导致服务状态可能不正确, 在此保证每次界面切换都能重新刷新状态
            updateServiceRunning()

            // 用户在系统权限设置中切换权限后再切换回应用时能及时更新状态
            updatePermissionState()

            // 自动重启无障碍服务
            fixRestartService()
        }
    }
}

private fun Activity.fixTransparentNavigationBar() {
    // 修复在浅色主题下导航栏背景不透明的问题
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    } else {
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
    }
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
    val state = stateFlow.collectAsState().value
    if (state) {
        val appId = "moe.shizuku.privileged.api"
        val appInfoCache = appInfoCacheFlow.collectAsState().value
        val installed = appInfoCache.contains(appId)
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
                        app.openUri("https://gkd.li?r=4")
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


val accessRestrictedSettingsShowFlow = MutableStateFlow(false)

@Composable
fun AccessRestrictedSettingsDlg() {
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsState()
    val navController = LocalNavController.current
    val isA11yPage = navController.currentDestination?.route == AuthA11YPageDestination.route
    LaunchedEffect(isA11yPage) {
        if (isA11yPage) {
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    if (accessRestrictedSettingsShow && !isA11yPage) {
        AlertDialog(
            title = {
                Text(text = "访问受限")
            },
            text = {
                Text(text = "尽管 GKD 已持有[写入安全设置权限], 但无法生效, 系统可能在更新 GKD 后用更高级的权限(访问受限设置)限制了 GKD, 请重新授权解除限制")
            },
            onDismissRequest = {
                accessRestrictedSettingsShowFlow.value = false
            },
            confirmButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                    navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                }) {
                    Text(text = "前往授权")
                }
            },
            dismissButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}