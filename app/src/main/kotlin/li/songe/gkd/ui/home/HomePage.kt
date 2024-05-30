package li.songe.gkd.ui.home

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.UriUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import li.songe.gkd.MainActivity
import li.songe.gkd.OpenFileActivity
import li.songe.gkd.OpenSchemeActivity
import li.songe.gkd.data.TransferData
import li.songe.gkd.data.importTransferData
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.json
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.readFileZipByteArray
import li.songe.gkd.util.toast

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
)

@RootNavGraph(start = true)
@Destination(style = ProfileTransitions::class)
@Composable
fun HomePage() {
    val context = LocalContext.current as MainActivity
    val vm = hiltViewModel<HomeVm>()
    val tab by vm.tabFlow.collectAsState()

    val controlPage = useControlPage()
    val subsPage = useSubsManagePage()
    val appListPage = useAppListPage()
    val settingsPage = useSettingsPage()

    val pages = arrayOf(controlPage, subsPage, appListPage, settingsPage)

    val currentPage = pages.find { p -> p.navItem.label == tab.label }
        ?: controlPage

    val intent = context.intent
    LaunchedEffect(key1 = intent, block = {
        intent ?: return@LaunchedEffect
        context.intent = null
        LogUtils.d(intent)
        val uri = intent.data ?: return@LaunchedEffect
        val source = intent.getStringExtra("source")
        if (source == OpenFileActivity::class.qualifiedName) {
            vm.viewModelScope.launchTry(Dispatchers.IO) {
                toast("加载导入...")
                vm.tabFlow.value = subsPage.navItem
                val string = readFileZipByteArray(
                    UriUtils.uri2Bytes(uri),
                    "${TransferData.TYPE}.json"
                )
                if (string != null) {
                    val transferData = withContext(Dispatchers.Default) {
                        json.decodeFromString<TransferData>(string)
                    }
                    val hasNewSubsItem = importTransferData(transferData)
                    toast("导入成功")
                    if (hasNewSubsItem) {
                        delay(1000)
                        checkSubsUpdate(true)
                    }
                } else {
                    toast("导入失败")
                }
            }
        } else if (source == OpenSchemeActivity::class.qualifiedName) {
            LogUtils.d(uri)
        }
    })

    Scaffold(
        modifier = currentPage.modifier,
        topBar = currentPage.topBar,
        floatingActionButton = currentPage.floatingActionButton,
        bottomBar = {
            NavigationBar {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = tab.label == page.navItem.label,
                        modifier = Modifier,
                        onClick = {
                            vm.tabFlow.value = page.navItem
                        },
                        icon = {
                            Icon(
                                imageVector = page.navItem.icon,
                                contentDescription = page.navItem.label
                            )
                        },
                        label = {
                            Text(text = page.navItem.label)
                        }
                    )
                }
            }
        },
        content = currentPage.content
    )
}