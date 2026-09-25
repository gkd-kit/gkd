package li.gkd.app.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.util.FolderUtils
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast

class ShareLogState(
    private val scope: CoroutineScope,
    private val githubUpload: GithubUploadState,
) {
    private val visibleFlow = MutableStateFlow(false)

    fun show() {
        visibleFlow.value = true
    }

    private fun dismiss() {
        visibleFlow.value = false
    }

    private fun share(context: MainActivity) {
        dismiss()
        scope.launchUi {
            val logZipFile = withContext(Dispatchers.IO) { FolderUtils.buildLogFile() }
            context.shareFile(logZipFile, UiStrings.logs_share_file)
        }
    }

    private fun save(context: MainActivity) {
        dismiss()
        scope.launchUi {
            FolderUtils.withTemporaryZip(
                create = { withContext(Dispatchers.IO) { FolderUtils.buildLogFile() } },
                delete = FolderUtils::deleteSharedFile,
            ) { logZipFile ->
                context.saveFileToDownloads(logZipFile)
            }
        }
    }

    private fun upload() {
        dismiss()
        val item = GithubUploadItem(
            label = UiStrings.logs_title,
            getFile = { FolderUtils.buildLogFile() },
            showHref = { "http://i.gkd.li/log/${it.id}" },
            releaseFile = FolderUtils::deleteSharedFile,
        )
        if (!githubUpload.startTask(item)) toast(UiStrings.upload_busy)
    }

    @Composable
    fun Render() {
        val visible by visibleFlow.collectAsStateWithLifecycle()
        if (visible) {
            val context = LocalActivity.current as MainActivity
            GkDialog(onDismissRequest = ::dismiss) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    val modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                    Text(
                        text = UiStrings.action_share,
                        modifier = Modifier
                            .clickable(onClick = throttle { share(context) })
                            .then(modifier),
                    )
                    Text(
                        text = UiStrings.action_save_to_downloads,
                        modifier = Modifier
                            .clickable(onClick = throttle { save(context) })
                            .then(modifier),
                    )
                    Text(
                        text = UiStrings.upload_generate_link,
                        modifier = Modifier
                            .clickable(onClick = throttle(::upload))
                            .then(modifier),
                    )
                }
            }
        }
    }
}
