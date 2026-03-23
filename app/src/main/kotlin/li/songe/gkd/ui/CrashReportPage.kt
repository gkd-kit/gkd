package li.songe.gkd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.ui.component.CopyTextCard
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.useScrollBehaviorState
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.itemVerticalPadding
import li.songe.gkd.util.ISSUES_URL
import li.songe.gkd.util.throttle


@Serializable
data object CrashReportRoute : NavKey

@Composable
fun CrashReportPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<CrashReportVm>()
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = {
                    Text(
                        text = "崩溃记录",
                        modifier = Modifier.noRippleClickable(onClick = throttle { scrollKey.intValue++ })
                    )
                },
            )
        },
        bottomBar = {
            if (vm.crashDataList.isNotEmpty()) {
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = throttle { mainVm.openUrl(ISSUES_URL) },
                    ) {
                        Text(text = "问题反馈")
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                    TextButton(
                        onClick = { mainVm.showShareLogDlgFlow.value = true },
                    ) {
                        Text(text = "导出日志")
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(itemVerticalPadding)
        ) {
            if (vm.crashDataList.isNotEmpty()) {
                vm.crashDataList.forEach { crashData ->
                    CopyTextCard(
                        text = crashData.stackTrace,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(EmptyHeight))
                EmptyText()
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
