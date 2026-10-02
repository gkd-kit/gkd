package li.gkd.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import li.gkd.app.platform.writeClipboardText
import li.gkd.app.resources.Res
import li.gkd.app.resources.copy_success
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.page.EditorSession
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.snapshot.SnapshotActions
import li.gkd.app.ui.snapshot.SnapshotViewModel
import li.gkd.app.ui.text.getSync
import li.gkd.app.ui.text.subscriptionMessage

typealias ConfirmDeletion = (String, String, () -> Set<DeletionTarget>, () -> Unit, suspend () -> Unit) -> Unit
typealias ShowRuleGroup = (Long, String?, RawSubscription.RawGroupProps, String?) -> Unit
typealias EditorFrame = @Composable (EditorSession, (() -> Unit)?, @Composable (PaddingValues) -> Unit) -> Unit
typealias SnapshotActionFactory = (SnapshotViewModel, (Long) -> Unit, () -> Unit, () -> Unit) -> SnapshotActions

fun copyText(text: String, showToast: (String) -> Unit) {
    writeClipboardText(text)
    showToast(Res.string.copy_success.getSync())
}

fun launchUi(scope: CoroutineScope, showToast: (String) -> Unit, block: suspend () -> Unit) =
    scope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showToast(e.subscriptionMessage())
        }
    }

fun launchUiAction(
    scope: CoroutineScope,
    showToast: (String) -> Unit,
    block: suspend () -> Unit
): () -> Unit = {
    launchUi(scope, showToast, block)
}

