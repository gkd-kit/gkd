package li.songe.gkd

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.blankj.utilcode.util.LogUtils
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.AuthA11YPageDestination
import com.ramcosta.composedestinations.utils.currentDestinationAsState
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.permission.AuthDialog
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.permission.updatePermissionState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.ManageService
import li.songe.gkd.service.fixRestartService
import li.songe.gkd.service.updateDefaultInputAppId
import li.songe.gkd.service.updateLauncherAppId
import li.songe.gkd.shizuku.execCommandForResult
import li.songe.gkd.ui.component.BuildDialog
import li.songe.gkd.ui.component.ShareDataDialog
import li.songe.gkd.ui.component.SubsSheet
import li.songe.gkd.ui.component.TermsAcceptDialog
import li.songe.gkd.ui.component.UrlDetailDialog
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.EditGithubCookieDlg
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.UpgradeDialog
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.componentName
import li.songe.gkd.util.fixSomeProblems
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.openApp
import li.songe.gkd.util.openUri
import li.songe.gkd.util.shizukuAppId
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.termsAcceptedFlow
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class MainActivity : ComponentActivity() {
    val startTime = System.currentTimeMillis()
    val mainVm by viewModels<MainViewModel>()
    val launcher by lazy { StartActivityLauncher(this) }
    val pickContentLauncher by lazy { PickContentLauncher(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        fixSomeProblems()
        super.onCreate(savedInstanceState)
        mainVm
        launcher
        pickContentLauncher
        ManageService.autoStart()
        lifecycleScope.launch {
            storeFlow.map(lifecycleScope) { s -> s.excludeFromRecents }.collect {
                activityManager.appTasks.forEach { task ->
                    task.setExcludeFromRecents(it)
                }
            }
        }
        setContent {
            val termsAccepted by termsAcceptedFlow.collectAsStateWithLifecycle()
            val navController = rememberNavController()
            mainVm.navController = navController
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalMainViewModel provides mainVm
            ) {
                AppTheme {
                    DestinationsNavHost(
                        navController = navController,
                        navGraph = NavGraphs.root
                    )
                    if (!termsAccepted) {
                        TermsAcceptDialog()
                    } else {
                        AccessRestrictedSettingsDlg()
                        ShizukuErrorDialog(mainVm.shizukuErrorFlow)
                        AuthDialog(mainVm.authReasonFlow)
                        BuildDialog(mainVm.dialogFlow)
                        mainVm.uploadOptions.ShowDialog()
                        EditGithubCookieDlg(mainVm.showEditCookieDlgFlow)
                        if (META.updateEnabled) {
                            UpgradeDialog(mainVm.updateStatus)
                        }
                        SubsSheet(mainVm, mainVm.sheetSubsIdFlow)
                        ShareDataDialog(mainVm, mainVm.showShareDataIdsFlow)
                        mainVm.inputSubsLinkOption.ContentDialog()
                        mainVm.ruleGroupState.Render()
                        UrlDetailDialog(mainVm.urlFlow)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activityVisibleFlow.update { it + 1 }
    }

    var isFirstResume = true
    override fun onResume() {
        super.onResume()
        if (isFirstResume && startTime - app.startTime < 2000) {
            isFirstResume = false
            return
        }
        syncFixState()
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
    A11yService.isRunning.value = A11yService.instance != null
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

            updateDefaultInputAppId()

            // 由于某些机型的进程存在 安装缓存/崩溃缓存 导致服务状态可能不正确, 在此保证每次界面切换都能重新刷新状态
            updateServiceRunning()

            // 用户在系统权限设置中切换权限后再切换回应用时能及时更新状态
            updatePermissionState()

            // 自动重启无障碍服务
            fixRestartService()
        }
    }
}

@Composable
private fun ShizukuErrorDialog(stateFlow: MutableStateFlow<Boolean>) {
    val state = stateFlow.collectAsState().value
    if (state) {
        val appInfoCache = appInfoCacheFlow.collectAsState().value
        val installed = appInfoCache.contains(shizukuAppId)
        AlertDialog(
            onDismissRequest = { stateFlow.value = false },
            title = { Text(text = "授权错误") },
            text = {
                Text(
                    text = if (installed) {
                        "Shizuku 授权失败, 请检查是否运行"
                    } else {
                        "Shizuku 授权失败, 检测到 Shizuku 未安装, 请先下载后安装, 如果你是通过其它方式授权, 请忽略此提示自行查找原因"
                    }
                )
            },
            confirmButton = {
                if (installed) {
                    TextButton(onClick = {
                        stateFlow.value = false
                        openApp(shizukuAppId)
                    }) {
                        Text(text = "打开 Shizuku")
                    }
                } else {
                    TextButton(onClick = {
                        stateFlow.value = false
                        openUri(ShortUrlSet.URL4)
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
    val a11yRunning by A11yService.isRunning.collectAsState()
    LaunchedEffect(a11yRunning) {
        if (a11yRunning) {
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsState()
    val navController = LocalNavController.current
    val currentDestination by navController.currentDestinationAsState()
    val isA11yPage = currentDestination?.route == AuthA11YPageDestination.route
    LaunchedEffect(isA11yPage, accessRestrictedSettingsShow) {
        if (isA11yPage && accessRestrictedSettingsShow && !a11yRunning) {
            toast("请重新授权以解除限制")
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    if (accessRestrictedSettingsShow && !isA11yPage && !a11yRunning) {
        AlertDialog(
            title = {
                Text(text = "权限受限")
            },
            text = {
                Text(text = "当前操作权限「访问受限设置」已被限制, 请先解除限制")
            },
            onDismissRequest = {
                accessRestrictedSettingsShowFlow.value = false
            },
            confirmButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                    navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                }) {
                    Text(text = "解除")
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

suspend fun MainActivity.grantPermissionByShizuku(command: String) {
    if (!appInfoCacheFlow.value.contains(shizukuAppId)) {
        mainVm.shizukuErrorFlow.value = true
    }
    if (shizukuOkState.stateFlow.value) {
        try {
            execCommandForResult(command)
            return
        } catch (e: Exception) {
            toast("运行失败:${e.message}")
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
    coroutineContext[Job]?.cancel()
    yield()
}