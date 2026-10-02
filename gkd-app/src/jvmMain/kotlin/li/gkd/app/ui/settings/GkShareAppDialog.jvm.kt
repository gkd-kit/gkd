package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import li.gkd.app.network.AppLinks
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.openExternalUrl
import li.gkd.app.ui.page.GkShareAppDialog

@Composable
actual fun AppWindow.GkShareAppDialog(visible: Boolean, onDismissRequest: () -> Unit) {
    if (visible) GkShareAppDialog(
        onDismissRequest,
        state::unsupported,
        state::unsupported,
        { openExternalUrl(AppLinks.PlayStore) })
}
