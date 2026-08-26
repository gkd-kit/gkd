package li.gkd.app.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.ui.component.CopyTextCard
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.util.ISSUES_URL
import li.gkd.app.util.throttle


@Serializable
data object CrashReportRoute : NavKey

@Composable
fun CrashReportPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { CrashReportVm(mainVm.takeCrashDataList()) }
    val pageScrollState = rememberColumnScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val scrollState = pageScrollState.scrollState
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
                        modifier = Modifier.noRippleClickable(onClick = throttle(pageScrollState::resetScroll))
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
                        onClick = { mainVm.shareLog.show() },
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
