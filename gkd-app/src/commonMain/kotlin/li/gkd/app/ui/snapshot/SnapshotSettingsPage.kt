package li.gkd.app.ui.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_id_invalid
import li.gkd.app.resources.event_selector_invalid
import li.gkd.app.resources.snapshot_trigger_config_required
import li.gkd.app.resources.update_success
import li.gkd.app.settings.ScreenshotConfigResult
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.page.SnapshotSettingsScreen
import li.gkd.app.ui.text.getSync

@Composable
fun SnapshotSettingsPage(
    window: AppWindow,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    toast: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    SnapshotSettingsScreen(
        store = store, screenshotServiceRunning = window.screenshotServiceRunning(),
        nativeScreenshotAvailable = window.nativeScreenshotAvailable(),
        onBack = onBack, onScreenshotService = window::setScreenshotServiceEnabled,
        onHelp = { onNavigate(WebViewRoute(AppLinks.SnapshotHelp)) },
        onSaveConfig = { appId, selector ->
            val result = SettingsRepository.saveCaptureScreenshotConfig(appId, selector)
            when (result) {
                ScreenshotConfigResult.Unchanged -> Unit
                ScreenshotConfigResult.Saved -> toast(Res.string.update_success.getSync())
                ScreenshotConfigResult.InvalidApp -> toast(Res.string.app_id_invalid.getSync())
                ScreenshotConfigResult.InvalidSelector -> toast(Res.string.event_selector_invalid.getSync())
            }
            result == ScreenshotConfigResult.Saved || result == ScreenshotConfigResult.Unchanged
        },
        onCaptureVolume = { enabled ->
            SettingsRepository.updateSettings {
                it.copy(
                    captureVolumeChange = enabled
                )
            }
        },
        onCaptureScreenshot = { enabled ->
            SettingsRepository.updateSettings { it.copy(captureScreenshot = enabled) }
            val current = SettingsRepository.settings.value
            if (enabled && (current.screenshotTargetAppId.isEmpty() || current.screenshotEventSelector.isEmpty())) {
                toast(Res.string.snapshot_trigger_config_required.getSync())
            }
        },
        onHideStatusBar = { enabled ->
            SettingsRepository.updateSettings {
                it.copy(
                    hideSnapshotStatusBar = enabled
                )
            }
        },
        onAutoSave = { enabled ->
            launchUi(scope, toast) {
                if (!enabled || window.ensureSnapshotSavePermission()) SettingsRepository.updateSettings {
                    it.copy(
                        autoSaveSnapshotToDownloads = enabled
                    )
                }
            }
        },
    )
}

@Composable
expect fun AppWindow.screenshotServiceRunning(): Boolean
expect fun AppWindow.nativeScreenshotAvailable(): Boolean
expect fun AppWindow.setScreenshotServiceEnabled(enabled: Boolean)
expect suspend fun AppWindow.ensureSnapshotSavePermission(): Boolean
