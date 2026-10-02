package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.about_easter_egg
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.openExternalUrl
import li.gkd.app.ui.page.AboutScreen
import li.gkd.app.ui.text.getSync

@Composable
fun AboutPage(
    window: AppWindow,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    toast: (String) -> Unit,
    update: li.gkd.app.ui.update.UpdateStatus?,
    onExportLogs: () -> Unit
) {
    val version = window.appVersion()
    var showVersionInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showShareAppDialog by rememberSaveable { mutableStateOf(false) }
    val store by settings.collectAsStateWithLifecycle()

    AboutScreen(
        appName = version.appName,
        versionName = version.versionName,
        isGkdChannel = version.isGkdChannel,
        updateChannelValue = store.updateChannel,
        updateAvailable = update != null,
        checkingUpdate = update?.checkUpdatingFlow?.collectAsStateWithLifecycle()?.value
            ?: false,
        onBack = onBack,
        onShare = { showShareAppDialog = true },
        onVersion = { showVersionInfoDialog = true },
        onLogo = { toast(Res.string.about_easter_egg.getSync()) },
        onSource = { window.openExternalUrl(AppLinks.Repository) },
        onDonate = { onNavigate(WebViewRoute(AppLinks.Donate)) },
        onTerms = { onNavigate(WebViewRoute(AppLinks.TermsOfService)) },
        onPrivacy = { onNavigate(WebViewRoute(AppLinks.PrivacyPolicy)) },
        onFeedback = { window.openExternalUrl(AppLinks.Issues) },
        onExportLogs = onExportLogs,
        onUpdateChannel = { value -> SettingsRepository.updateSettings { it.copy(updateChannel = value) } },
        onCheckUpdate = { update?.checkUpdate(true) },
    )

    li.gkd.app.ui.page.GkVersionInfoDialog(
        showVersionInfoDialog, version.channel, version.versionCode, version.versionName,
        version.commitLabel, version.commitTime, { window.openExternalUrl(version.commitUrl) },
        { showVersionInfoDialog = false },
    )
    window.GkShareAppDialog(showShareAppDialog) { showShareAppDialog = false }
}

@Composable
expect fun AppWindow.GkShareAppDialog(visible: Boolean, onDismissRequest: () -> Unit)
