package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import li.gkd.app.resources.Res
import li.gkd.app.resources.logs_share_file
import li.gkd.app.resources.logs_title
import li.gkd.app.resources.upload_busy
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.storage.appStorage
import li.gkd.app.storage.createLogArchive
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.saveArchive
import li.gkd.app.ui.share.shareArchive
import li.gkd.app.ui.text.getSync
import li.gkd.app.ui.upload.GithubUploadItem
import li.gkd.app.ui.upload.GithubUploadState
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

class ShareLogState(
    private val scope: CoroutineScope,
    private val githubUpload: GithubUploadState,
) {
    private var visible by mutableStateOf(false)

    fun show() { visible = true }
    private fun dismiss() { visible = false }

    private fun share(host: UiHost) {
        dismiss()
        scope.launchUi {
            host.shareArchive(getString(Res.string.logs_share_file), ::createLogArchive)
        }
    }

    private fun save(host: UiHost) {
        dismiss()
        scope.launchUi { host.saveArchive(::createLogArchive) }
    }

    private fun upload() {
        dismiss()
        val item = GithubUploadItem(
            label = Res.string.logs_title.getSync(),
            getFile = ::createLogArchive,
            showHref = { "http://i.gkd.li/log/${it.id}" },
            releaseFile = { StorageMaintenance.deleteSharedFile(appStorage(), it) },
        )
        if (!githubUpload.startTask(item)) ToastUtils.show(Res.string.upload_busy.getSync())
    }

    @Composable
    fun Render(host: UiHost) {
        if (visible) GkShareLogDialog(::dismiss, { share(host) }, { save(host) }, ::upload)
    }
}
