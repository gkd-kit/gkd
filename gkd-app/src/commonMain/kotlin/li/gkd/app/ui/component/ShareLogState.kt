package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.storage.appStorage
import li.gkd.app.storage.createLogArchive
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.saveArchive
import li.gkd.app.ui.share.shareArchive
import li.gkd.app.ui.upload.GithubUploadItem
import li.gkd.app.ui.upload.GithubUploadState
import li.gkd.app.util.ToastUtils
import li.gkd.db.MatchDiagnosticRecord
import org.jetbrains.compose.resources.getString

class ShareLogState(
    private val scope: CoroutineScope,
    private val githubUpload: GithubUploadState,
) {
    private val diagnostics = MutableStateFlow<List<MatchDiagnosticRecord>?>(null)

    fun show(diagnostics: List<MatchDiagnosticRecord> = emptyList()) {
        this.diagnostics.value = diagnostics.toList()
    }

    private fun dismiss() {
        diagnostics.value = null
    }

    private suspend fun upload(diagnostics: List<MatchDiagnosticRecord>) {
        val item =
            GithubUploadItem(
                label = getString(Res.string.logs_title),
                getFile = { createLogArchive(diagnostics) },
                showHref = { AppLinks.log(it.id) },
                releaseFile = { StorageMaintenance.deleteSharedFile(appStorage(), it) },
            )
        if (!githubUpload.startTask(item)) ToastUtils.show(getString(Res.string.upload_busy))
    }

    @Composable
    fun Render(host: UiHost) {
        val requestedDiagnostics by diagnostics.collectAsStateWithLifecycle()
        requestedDiagnostics?.let { targets ->
            GkShareLogDialog(
                onDismiss = ::dismiss,
                onShare = {
                    dismiss()
                    scope.launchUi {
                        host.shareArchive(getString(Res.string.logs_share_file)) {
                            createLogArchive(targets)
                        }
                    }
                },
                onSave = {
                    dismiss()
                    scope.launchUi { host.saveArchive { createLogArchive(targets) } }
                },
                onUpload = {
                    dismiss()
                    scope.launchUi { upload(targets) }
                },
            )
        }
    }
}
