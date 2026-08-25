package li.songe.gkd

import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.a11y.updateSystemDefaultAppId
import li.songe.gkd.a11y.updateTopActivity
import li.songe.gkd.permission.PermissionRequests
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.priv.privilegeContextFlow
import li.songe.gkd.service.StatusService
import li.songe.gkd.service.fixRestartAutomatorService
import li.songe.gkd.service.updateTopTaskAppId
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.ActivityImeController
import li.songe.gkd.ui.share.ActivityResultRequests
import li.songe.gkd.ui.share.FixedWindowInsets
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.app.AppRoot
import li.songe.gkd.util.BarUtils
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.SystemDownloads
import li.songe.gkd.util.fixSomeProblems
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapState
import li.songe.gkd.util.toast
import li.songe.gkd.util.tryStartActivity
import java.io.File
import kotlin.concurrent.Volatile
import kotlin.reflect.jvm.jvmName

class MainActivity : ComponentActivity() {
    val startTime = System.currentTimeMillis()
    val mainVm by viewModels<MainViewModel>()
    val imeController = ActivityImeController(this)
    private val activityResultHost = ActivityResultRequests.Host(this)
    private val permissionRequestHost = PermissionRequests.Host(this)

    var topBarWindowInsets by mutableStateOf(WindowInsets(top = BarUtils.getStatusBarHeight()))

    fun shareFile(file: File, title: String) {
        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        tryStartActivity(Intent.createChooser(intent, title))
    }

    suspend fun saveFileToDownloads(file: File) {
        if (!mainVm.permissionRequests.ensurePermissions(PermissionStates.writeExternalStorage)) {
            return
        }
        if (!SystemDownloads.save(file)) return
        toast("已保存 ${file.name} 到下载")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        fixSomeProblems()
        super.onCreate(savedInstanceState)
        activityResultHost.bind(mainVm.activityResults)
        permissionRequestHost.bind(mainVm.permissionRequests)
        LogUtils.d()
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
        StatusService.autoStart()
        if (storeFlow.value.enableBlockA11yAppList) {
            updateTopTaskAppId(META.appId)
        }
        setContent {
            CompositionLocalProvider(
                LocalMainViewModel provides mainVm,
            ) {
                val latestInsets = TopAppBarDefaults.windowInsets
                val density = LocalDensity.current
                if (latestInsets.getTop(density) > topBarWindowInsets.getTop(density)) {
                    topBarWindowInsets = FixedWindowInsets(latestInsets)
                }
                AppRoot()
                LaunchedEffect(null) {
                    intent?.let {
                        mainVm.handleIntent(it)
                        intent = null
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LogUtils.d()
        activityVisibleState++
        if (topActivityFlow.value.appId != META.appId) {
            synchronized(topActivityFlow) {
                updateTopActivity(
                    META.appId,
                    MainActivity::class.jvmName
                )
            }
        }
    }

    var isFirstResume = true
    override fun onResume() {
        super.onResume()
        LogUtils.d()
        if (isFirstResume && startTime - app.startTime < 2000) {
            isFirstResume = false
        } else {
            syncFixState()
        }
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d()
        activityVisibleState--
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtils.d()
    }
}

@Volatile
private var activityVisibleState = 0
val isActivityVisible get() = activityVisibleState > 0

private val syncStateMutex = Mutex()
fun syncFixState() {
    appScope.launchTry(Dispatchers.IO) {
        if (syncStateMutex.isLocked) {
            LogUtils.d("syncFixState isLocked")
        }
        syncStateMutex.withLock {
            updateSystemDefaultAppId()
            privilegeContextFlow.value?.grantSelf()
            PermissionStates.refreshAll()
            fixRestartAutomatorService()
        }
    }
}
