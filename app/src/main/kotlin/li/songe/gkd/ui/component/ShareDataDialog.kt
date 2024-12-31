package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.MainActivity
import li.songe.gkd.data.exportData
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.saveFileToDownloads
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.throttle

@Composable
fun ShareDataDialog(
    vm: ViewModel,
    showShareDataIdsFlow: MutableStateFlow<Set<Long>?>,
) {
    val showShareDataIds = showShareDataIdsFlow.collectAsState().value
    if (showShareDataIds != null) {
        val context = LocalContext.current as MainActivity
        Dialog(onDismissRequest = { showShareDataIdsFlow.value = null }) {
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
                            showShareDataIdsFlow.value = null
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val file = exportData(showShareDataIds)
                                context.shareFile(file, "分享数据文件")
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "保存到下载",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareDataIdsFlow.value = null
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val file = exportData(showShareDataIds)
                                context.saveFileToDownloads(file)
                            }
                        })
                        .then(modifier)
                )
            }
        }
    }
}