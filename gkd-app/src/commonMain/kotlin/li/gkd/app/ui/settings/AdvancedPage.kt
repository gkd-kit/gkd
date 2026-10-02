package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.http_port_input_hint
import li.gkd.app.resources.update_success
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.openExternalUrl
import li.gkd.app.ui.page.AdvancedScreen
import li.gkd.app.ui.text.getSync

@Composable
fun AdvancedPage(
    window: AppWindow,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    toast: (String) -> Unit,
    onCookieEdit: () -> Unit
) {
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    AdvancedScreen(
        store = store,
        httpRunning = window.httpRunning(),
        localNetworkIps = window.localNetworkIps(),
        buttonServiceRunning = window.snapshotButtonRunning(),
        activityServiceRunning = window.activityMonitorRunning(),
        eventServiceRunning = window.eventMonitorRunning(),
        trackServiceRunning = window.trackServiceRunning(),
        onBack = onBack,
        onNavigate = onNavigate,
        onSnapshotButton = window::setSnapshotButtonEnabled,
        onHttp = window::setHttpServiceEnabled,
        onActivity = window::setActivityMonitorEnabled,
        onEvent = window::setEventMonitorEnabled,
        onTrack = window::setTrackServiceEnabled,
        onAddress = window::openExternalUrl,
        onCookieHelp = { onNavigate(WebViewRoute(AppLinks.CookieHelp)) },
        onCookieEdit = onCookieEdit,
        onAutoClear = { enabled -> SettingsRepository.updateSettings { it.copy(autoClearMemorySubs = enabled) } },
        onSavePort = { text ->
            val oldPort = SettingsRepository.settings.value.httpServerPort
            val saved = SettingsRepository.saveHttpServerPort(text)
            if (!saved) toast(Res.string.http_port_input_hint.getSync())
            else if (oldPort != text.toInt()) toast(Res.string.update_success.getSync())
            saved
        },
    )
}

expect fun AppWindow.setSnapshotButtonEnabled(enabled: Boolean)
expect fun AppWindow.setHttpServiceEnabled(enabled: Boolean)
expect fun AppWindow.setActivityMonitorEnabled(enabled: Boolean)
expect fun AppWindow.setEventMonitorEnabled(enabled: Boolean)
expect fun AppWindow.setTrackServiceEnabled(enabled: Boolean)
@Composable
expect fun AppWindow.httpRunning(): Boolean
@Composable
expect fun AppWindow.localNetworkIps(): List<String>
@Composable
expect fun AppWindow.snapshotButtonRunning(): Boolean
@Composable
expect fun AppWindow.activityMonitorRunning(): Boolean
@Composable
expect fun AppWindow.eventMonitorRunning(): Boolean
@Composable
expect fun AppWindow.trackServiceRunning(): Boolean
