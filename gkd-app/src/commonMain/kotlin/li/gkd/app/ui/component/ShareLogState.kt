package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import li.gkd.app.resources.*
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.storage.appStorage
import li.gkd.app.storage.createLogArchive
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.upload.GithubUploadItem
import li.gkd.app.ui.upload.GithubUploadState
import li.gkd.app.util.ToastUtils

class ShareLogState(
    private val scope: CoroutineScope,
    private val githubUpload: GithubUploadState,
) {
    private var visible by mutableStateOf(false)

    fun show() {
        visible = true
    }

    private fun dismiss() {
        visible = false
    }

    private fun upload() {
        dismiss()
        val item =
            GithubUploadItem(
                label = Res.string.logs_title.getSync(),
                getFile = ::createLogArchive,
                showHref = { "http://i.gkd.li/log/${it.id}" },
                releaseFile = { StorageMaintenance.deleteSharedFile(appStorage(), it) },
            )
        if (!githubUpload.startTask(item)) ToastUtils.show(Res.string.upload_busy.getSync())
    }

    @Composable
    fun Render(onShare: suspend () -> Unit, onSave: suspend () -> Unit) {
        if (visible) {
            GkShareLogDialog(
                onDismiss = ::dismiss,
                onShare = {
                    dismiss()
                    scope.launchUi { onShare() }
                },
                onSave = {
                    dismiss()
                    scope.launchUi { onSave() }
                },
                onUpload = ::upload,
            )
        }
    }
}
