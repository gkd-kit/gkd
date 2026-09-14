package li.gkd.app.ui.app

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import li.gkd.app.ui.component.AppDialog
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.AppTheme

@Composable
fun AppRoot() {
    val mainVm = LocalMainViewModel.current
    val context = LocalContext.current
    var showNotice by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("gkd_inspect", Context.MODE_PRIVATE)
        showNotice = !prefs.getBoolean("third_party_notice_shown", false)
    }
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            MainNavigation()
            AppOverlayHost()
            mainVm.permissionRequests.Render(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
            )
        }
    }
    if (showNotice) {
        AppDialog(onDismissRequest = { showNotice = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "关于本版本",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "本应用是基于开源项目 GKD(https://github.com/gkd-kit/gkd) 的第三方修改版, 在原版基础上新增/改动以下内容:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "1. 快照审查器: 抓取快照后在记录菜单选择\"生成跳过广告规则\", 自动识别广告关闭按钮并生成规则, 一键保存到本地订阅, 立即生效\n\n2. 内置三条广告订阅(AIsouler/甘霖/梦念逍遥), 首次启动自动加载, 每次启动自动检查更新\n\n3. 原版\"查看\"拆分出\"生成跳过广告规则\"和\"查看截图\"两个入口",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "使用方式:\n① 抓到快照后, 在快照记录里点该条记录的\"生成跳过广告规则\"\n② 点击截图或下方节点树选中广告按钮(绿点=可点击)\n③ 点\"保存此节点为规则\"或底部\"一键保存识别结果\"\n\n订阅与规则均可在\"订阅\"页面手动管理/删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            context.getSharedPreferences("gkd_inspect", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("third_party_notice_shown", true)
                                .apply()
                            showNotice = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("我知道了")
                    }
                }
            }
        }
    }
}
