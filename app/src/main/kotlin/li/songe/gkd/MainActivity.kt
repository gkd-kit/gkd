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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.AuthA11YPageDestination
import com.ramcosta.composedestinations.utils.currentDestinationAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.a11y.topAppIdFlow
import li.songe.gkd.a11y.updateSystemDefaultAppId
import li.songe.gkd.a11y.updateTopActivity
import li.songe.gkd.permission.AuthDialog
import li.songe.gkd.permission.updatePermissionState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.ButtonService
import li.songe.gkd.service.HttpService
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.service.StatusService
import li.songe.gkd.service.fixRestartService
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.BuildDialog
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.ShareDataDialog
import li.songe.gkd.ui.component.SubsSheet
import li.songe.gkd.ui.component.TermsAcceptDialog
import li.songe.gkd.ui.component.TextDialog
import li.songe.gkd.ui.share.FixedWindowInsets
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.LocalNavController
import li.songe.gkd.ui.style.AppTheme
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.EditGithubCookieDlg
import li.songe.gkd.util.KeyboardUtils
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.componentName
import li.songe.gkd.util.copyText
import li.songe.gkd.util.fixSomeProblems
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapState
import li.songe.gkd.util.openApp
import li.songe.gkd.util.openUri
import li.songe.gkd.util.shizukuAppId
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class MainActivity : ComponentActivity() {
    val startTime = System.currentTimeMillis()
    val mainVm by viewModels<MainViewModel>()
    val launcher by lazy { StartActivityLauncher(this) }
    val pickContentLauncher by lazy { PickContentLauncher(this) }

    val imeFullHiddenFlow = MutableStateFlow(true)
    val imePlayingFlow = MutableStateFlow(false)

    private val imeVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(window.decorView)!!
            .isVisible(WindowInsetsCompat.Type.ime())

    var topBarWindowInsets by mutableStateOf(WindowInsets())

    private fun watchKeyboardVisible() {
        if (AndroidTarget.R) {
            ViewCompat.setWindowInsetsAnimationCallback(
                window.decorView,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onStart(
                        animation: WindowInsetsAnimationCompat,
                        bounds: WindowInsetsAnimationCompat.BoundsCompat
                    ): WindowInsetsAnimationCompat.BoundsCompat {
                        imePlayingFlow.update { imeVisible }
                        return super.onStart(animation, bounds)
                    }

                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: List<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        imeFullHiddenFlow.update { !imeVisible }
                        imePlayingFlow.update { false }
                        super.onEnd(animation)
                    }
                })
        } else {
            KeyboardUtils.registerSoftInputChangedListener(window) { height ->
                // onEnd
                imeFullHiddenFlow.update { height == 0 }
            }
        }
    }

    suspend fun hideSoftInput(): Boolean {
        if (!imeFullHiddenFlow.updateAndGet { !imeVisible }) {
            KeyboardUtils.hideSoftInput(this@MainActivity)
            imeFullHiddenFlow.drop(1).first()
            return true
        }
        return false
    }

    fun justHideSoftInput(): Boolean {
        if (!imeFullHiddenFlow.updateAndGet { !imeVisible }) {
            KeyboardUtils.hideSoftInput(this@MainActivity)
            return true
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        fixSomeProblems()
        super.onCreate(savedInstanceState)
        mainVm
        launcher
        pickContentLauncher
        lifecycleScope.launch {
            storeFlow.mapState(lifecycleScope) { s -> s.excludeFromRecents }.collect {
                app.activityManager.appTasks.forEach { task ->
                    task.setExcludeFromRecents(it)
                }
            }
        }
        addOnNewIntentListener {
            mainVm.handleIntent(it)
            intent = null
        }
        watchKeyboardVisible()
        StatusService.autoStart()
        if (storeFlow.value.enableBlockA11yAppList) {
            topAppIdFlow.value = META.appId
        }
        setContent {
            val latestInsets = TopAppBarDefaults.windowInsets
            val density = LocalDensity.current
            if (latestInsets.getTop(density) > topBarWindowInsets.getTop(density)) {
                topBarWindowInsets = FixedWindowInsets(latestInsets)
            }
            val navController = rememberNavController()
            mainVm.updateNavController(navController)
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalMainViewModel provides mainVm
            ) {
                AppTheme {
                    DestinationsNavHost(
                        navController = navController,
                        navGraph = NavGraphs.root
                    )
                    if (!mainVm.termsAcceptedFlow.collectAsState().value) {
                        TermsAcceptDialog()
                    } else {
                        AccessRestrictedSettingsDlg()
                        ShizukuErrorDialog(mainVm.shizukuErrorFlow)
                        AuthDialog(mainVm.authReasonFlow)
                        BuildDialog(mainVm.dialogFlow)
                        mainVm.uploadOptions.ShowDialog()
                        EditGithubCookieDlg()
                        mainVm.updateStatus?.UpgradeDialog()
                        SubsSheet(mainVm, mainVm.sheetSubsIdFlow)
                        ShareDataDialog(mainVm, mainVm.showShareDataIdsFlow)
                        mainVm.inputSubsLinkOption.ContentDialog()
                        mainVm.ruleGroupState.Render()
                        TextDialog(mainVm.textFlow)
                    }
                }
            }
            LaunchedEffect(null) {
                intent?.let {
                    mainVm.handleIntent(it)
                    intent = null
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activityVisibleState++
        if (topActivityFlow.value.appId != META.appId) {
            updateTopActivity(META.appId, MainActivity::class.jvmName)
        }
    }

    var isFirstResume = true
    override fun onResume() {
        super.onResume()
        if (isFirstResume && startTime - app.startTime < 2000) {
            isFirstResume = false
        } else {
            syncFixState()
        }
    }

    override fun onStop() {
        super.onStop()
        activityVisibleState--
    }

    private var lastBackPressedTime = 0L

    @Suppress("OVERRIDE_DEPRECATION", "GestureBackNavigation")
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

@Volatile
private var activityVisibleState = 0
fun isActivityVisible() = activityVisibleState > 0

val activityNavSourceName by lazy { META.appId + ".activity.nav.source" }

fun Activity.navToMainActivity() {
    if (intent != null) {
        val navIntent = Intent(intent)
        navIntent.component = MainActivity::class.componentName
        navIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        navIntent.putExtra(activityNavSourceName, this::class.jvmName)
        startActivity(navIntent)
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
    StatusService.isRunning.value = checkRunning(StatusService::class)
    ButtonService.isRunning.value = checkRunning(ButtonService::class)
    ScreenshotService.isRunning.value = checkRunning(ScreenshotService::class)
    HttpService.isRunning.value = checkRunning(HttpService::class)
}

private val syncStateMutex = Mutex()
fun syncFixState() {
    appScope.launchTry(Dispatchers.IO) {
        syncStateMutex.withLock {
            updateSystemDefaultAppId()
            updateServiceRunning()
            updatePermissionState()
            fixRestartService()
        }
    }
}

@Composable
private fun ShizukuErrorDialog(stateFlow: MutableStateFlow<Throwable?>) {
    val state = stateFlow.collectAsState().value
    if (state != null) {
        val errorText = remember { state.stackTraceToString() }
        val appInfoCache = appInfoMapFlow.collectAsState().value
        val installed = appInfoCache.contains(shizukuAppId)
        AlertDialog(
            onDismissRequest = { stateFlow.value = null },
            title = { Text(text = "授权错误") },
            text = {
                Column {
                    Text(
                        text = if (installed) {
                            "Shizuku 授权失败, 请检查是否运行"
                        } else {
                            "Shizuku 授权失败, 检测到 Shizuku 未安装, 请先下载后安装, 如果你是通过其它方式授权, 请忽略此提示自行查找原因"
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = errorText,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(8.dp)
                                    .heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        PerfIcon(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable(onClick = throttle {
                                    copyText(errorText)
                                })
                                .padding(4.dp)
                                .size(20.dp),
                            imageVector = PerfIcon.ContentCopy,
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                        )
                    }
                }
            },
            confirmButton = {
                if (installed) {
                    TextButton(onClick = {
                        stateFlow.value = null
                        openApp(shizukuAppId)
                    }) {
                        Text(text = "打开 Shizuku")
                    }
                } else {
                    TextButton(onClick = {
                        stateFlow.value = null
                        openUri(ShortUrlSet.URL4)
                    }) {
                        Text(text = "去下载")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { stateFlow.value = null }) {
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
    val mainVm = LocalMainViewModel.current
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
                    mainVm.navigatePage(AuthA11YPageDestination)
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
