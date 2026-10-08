package li.gkd.app.ui.settings

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import li.gkd.app.network.AppLinks
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.page.GkShareAppDialog
import li.gkd.app.ui.platform.UiHost

@Composable
actual fun UiHost.GkShareAppDialog(
    visible: Boolean,
    dialogs: DialogRequests,
    scope: CoroutineScope,
    onOpenUrl: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (visible) GkShareAppDialog(
        onDismissRequest,
        state::unsupported,
        state::unsupported,
        { onOpenUrl(AppLinks.PlayStore) })
}
