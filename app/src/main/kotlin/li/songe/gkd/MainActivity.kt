package li.songe.gkd

import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.RequestPermissionLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import li.songe.gkd.composition.CompositionActivity
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.service.updateLauncherAppId
import li.songe.gkd.ui.NavGraphs
import li.songe.gkd.ui.component.ConfirmDialog
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.AuthDialog
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.LocalPickContentLauncher
import li.songe.gkd.util.LocalRequestPermissionLauncher
import li.songe.gkd.util.UpgradeDialog
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow

@AndroidEntryPoint
class MainActivity : CompositionActivity({
    useLifeCycleLog()
    val launcher = StartActivityLauncher(this)
    val pickContentLauncher = PickContentLauncher(this)
    val requestPermissionLauncher = RequestPermissionLauncher(this)

    lifecycleScope.launch {
        storeFlow.map(lifecycleScope) { s -> s.excludeFromRecents }.collect {
            (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let { manager ->
                manager.appTasks.forEach { task ->
                    task?.setExcludeFromRecents(it)
                }
            }
        }
    }

    // 每次打开页面更新记录桌面 appId
    updateLauncherAppId()

    setContent {
        val navController = rememberNavController()

        AppTheme {
            ConfirmDialog()
            AuthDialog()
            UpgradeDialog()
            CompositionLocalProvider(
                LocalLauncher provides launcher,
                LocalPickContentLauncher provides pickContentLauncher,
                LocalRequestPermissionLauncher provides requestPermissionLauncher,
                LocalNavController provides navController
            ) {
                DestinationsNavHost(
                    navGraph = NavGraphs.root, navController = navController, modifier = Modifier
                )
            }
        }
    }
})




