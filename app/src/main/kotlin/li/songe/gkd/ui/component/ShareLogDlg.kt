package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.MainActivity
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.util.buildLogFile
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.saveFileToDownloads
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.throttle

@Composable
fun ShareLogDlg(showShareLogDlgFlow: MutableStateFlow<Boolean>) {
    var visible by showShareLogDlgFlow.asMutableState()
    if (visible) {
        val mainVm = LocalMainViewModel.current
        val context = LocalActivity.current as MainActivity
        Dialog(onDismissRequest = { visible = false }) {
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
                    text = "分享到其他应用", modifier = Modifier
                        .clickable(onClick = throttle {
                            visible = false
                            mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.shareFile(logZipFile, "分享日志文件")
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "保存到下载", modifier = Modifier
                        .clickable(onClick = throttle {
                            visible = false
                            mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.saveFileToDownloads(logZipFile)
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "生成链接(需科学上网)",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            visible = false
                            mainVm.uploadOptions.startTask(
                                getFile = { buildLogFile() }
                            )
                        })
                        .then(modifier)
                )
            }
        }
    }
}