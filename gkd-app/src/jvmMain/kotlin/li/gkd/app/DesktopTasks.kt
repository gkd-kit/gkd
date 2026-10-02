package li.gkd.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import li.gkd.app.network.AppLinks
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.update.UpdateStatus
import li.gkd.app.ui.upload.GithubUploadState

/** Application tasks survive navigation and explicit scenario reloads. */
class DesktopTasks(state: DesktopState) :
    AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val githubUpload = GithubUploadState(
        scope,
        { state.navigate(WebViewRoute(AppLinks.CookieHelp)) },
        state.toast::show
    )
    val updateStatus = UpdateStatus(
        scope,
        DesktopProfile.versionCode.toIntOrNull() ?: 0,
        DesktopProfile.versionName,
        state.toast::show,
        { state.simulator.settings.value.networkAvailable },
    )

    override fun close() {
        scope.cancel()
    }
}
