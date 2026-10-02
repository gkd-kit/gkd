package li.gkd.app.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import li.gkd.app.MainActivity
import li.gkd.app.data.LogMetadataSources
import li.gkd.app.resources.Res
import li.gkd.app.resources.logs_share_file
import li.gkd.app.resources.logs_title
import li.gkd.app.resources.upload_busy
import li.gkd.app.storage.FileExports
import li.gkd.app.storage.LogArchive
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.text.getSync
import li.gkd.app.ui.upload.GithubUploadItem
import li.gkd.app.ui.upload.GithubUploadState
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils

class ShareLogState(
    private val scope: CoroutineScope,
    private val githubUpload: GithubUploadState,
) {
    private val visibleFlow = MutableStateFlow(false)

    private fun buildArchive(): java.io.File {
        LogUtils.flush()
        return LogArchive.build(LogMetadataSources.sources())
    }

    fun show() {
        visibleFlow.value = true
    }

    private fun dismiss() {
        visibleFlow.value = false
    }

    private fun share(context: MainActivity) {
        dismiss()
        scope.launchUi {
            val logZipFile = withContext(Dispatchers.IO) { buildArchive() }
            context.shareFile(logZipFile, Res.string.logs_share_file.getSync())
        }
    }

    private fun save(context: MainActivity) {
        dismiss()
        scope.launchUi {
            FileExports.withTemporaryFile(
                create = { withContext(Dispatchers.IO) { buildArchive() } },
                delete = { StorageMaintenance.deleteSharedFile(AndroidStorage.storage, it) },
            ) { logZipFile ->
                context.saveFileToDownloads(logZipFile)
            }
        }
    }

    private fun upload() {
        dismiss()
        val item = GithubUploadItem(
            label = Res.string.logs_title.getSync(),
            getFile = { buildArchive() },
            showHref = { "http://i.gkd.li/log/${it.id}" },
            releaseFile = { StorageMaintenance.deleteSharedFile(AndroidStorage.storage, it) },
        )
        if (!githubUpload.startTask(item)) ToastUtils.show(Res.string.upload_busy.getSync())
    }

    @Composable
    fun Render() {
        val visible by visibleFlow.collectAsStateWithLifecycle()
        if (visible) {
            val context = LocalActivity.current as MainActivity
            GkShareLogDialog(
                ::dismiss,
                { share(context) },
                { save(context) },
                ::upload
            )
        }
    }
}
