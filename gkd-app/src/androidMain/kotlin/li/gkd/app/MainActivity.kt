package li.gkd.app

import android.content.Intent
import android.graphics.Color
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
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import li.gkd.app.development.DesktopProfileExport
import li.gkd.app.permission.PermissionRequests
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.PlatformResult
import li.gkd.app.platform.lifecycle.onCreated
import li.gkd.app.platform.lifecycle.useLogLifecycle
import li.gkd.app.platform.lifecycle.useMainActivityLifecycle
import li.gkd.app.resources.Res
import li.gkd.app.resources.file_saved_to_downloads
import li.gkd.app.resources.platform_action_unsupported
import li.gkd.app.service.StatusService
import li.gkd.app.service.updateTopTaskAppId
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.storage.saveToDownloads
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.app.AppRoot
import li.gkd.app.ui.component.LocalTopBarWindowInsets
import li.gkd.app.ui.share.ActivityImeController
import li.gkd.app.ui.share.ActivityResultRequests
import li.gkd.app.ui.share.FixedWindowInsets
import li.gkd.app.ui.theme.installThemedSplashScreen
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.BarUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.mapState
import li.gkd.app.util.tryStartActivity
import org.jetbrains.compose.resources.getString
import java.io.File

class MainActivity : ComponentActivity() {
    val mainVm by viewModels<MainViewModel>()
    val imeController = ActivityImeController(this)
    private val activityResultHost = ActivityResultRequests.Host(this)
    private val permissionRequestHost = PermissionRequests.Host(this)

    var topBarWindowInsets by mutableStateOf(WindowInsets(top = BarUtils.getStatusHeight()))

    init {
        useMainActivityLifecycle()
        useLogLifecycle()
        onCreated {
            lifecycleScope.launch {
                settings.mapState(lifecycleScope) { s -> s.excludeFromRecents }.collect {
                    app.activityManager.appTasks.forEach { task ->
                        task.setExcludeFromRecents(it)
                    }
                }
            }
        }
    }

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
        val result = saveToDownloads(file)
        if (result !is PlatformResult.Success) {
            ToastUtils.show(getString(Res.string.platform_action_unsupported))
            return
        }
        val savedName = result.value
        ToastUtils.show(getString(Res.string.file_saved_to_downloads, savedName))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installThemedSplashScreen()
        enableEdgeToEdge()
        fixTransparentNavigationBar()
        super.onCreate(savedInstanceState)
        activityResultHost.bind(mainVm.activityResults)
        permissionRequestHost.bind(mainVm.permissionRequests)
        mainVm.registerCurrent()
        addOnNewIntentListener {
            if (!DesktopProfileExport.handle(this@MainActivity, it)) mainVm.handleIntent(it)
            intent = null
        }
        StatusService.autoStart()
        if (settings.value.enableBlockA11yAppList) {
            updateTopTaskAppId(META.appId)
        }
        setContent {
            val latestInsets = TopAppBarDefaults.windowInsets
            val density = LocalDensity.current
            if (latestInsets.getTop(density) > topBarWindowInsets.getTop(density)) {
                topBarWindowInsets = FixedWindowInsets(latestInsets)
            }
            CompositionLocalProvider(
                LocalTopBarWindowInsets provides topBarWindowInsets,
            ) { AppRoot() }
            LaunchedEffect(null) {
                intent?.let {
                    if (!DesktopProfileExport.handle(this@MainActivity, it)) mainVm.handleIntent(it)
                    intent = null
                }
            }
        }
    }

    private fun fixTransparentNavigationBar() {
        // 修复在浅色主题下导航栏背景不透明的问题
        if (AndroidTarget.Q) {
            window.isNavigationBarContrastEnforced = false
        } else {
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
    }
}
