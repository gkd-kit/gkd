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
import li.gkd.app.MainActivity
import li.gkd.app.util.buildLogFile
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle

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
        scope.launchTry {
            val logZipFile = withContext(Dispatchers.IO) { buildLogFile() }
            context.shareFile(logZipFile, "分享日志文件")
        }
    }

    private fun save(context: MainActivity) {
        dismiss()
        scope.launchTry {
            val logZipFile = withContext(Dispatchers.IO) { buildLogFile() }
            context.saveFileToDownloads(logZipFile)
        }
    }

    private fun upload() {
        dismiss()
        githubUpload.startTask(
            getFile = { buildLogFile() },
            showHref = { "http://i.gkd.li/log/${it.id}" },
        )
    }

    @Composable
    fun Render() {
        val visible by visibleFlow.collectAsStateWithLifecycle()
        if (visible) {
            val context = LocalActivity.current as MainActivity
            AppDialog(onDismissRequest = ::dismiss) {
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
                        text = "分享到其他应用",
                        modifier = Modifier
                            .clickable(onClick = throttle { share(context) })
                            .then(modifier),
                    )
                    Text(
                        text = "保存到下载",
                        modifier = Modifier
                            .clickable(onClick = throttle { save(context) })
                            .then(modifier),
                    )
                    Text(
                        text = "生成链接(需科学上网)",
                        modifier = Modifier
                            .clickable(onClick = throttle(::upload))
                            .then(modifier),
                    )
                }
            }
        }
    }
}
