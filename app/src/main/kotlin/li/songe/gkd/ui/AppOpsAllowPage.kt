package li.songe.gkd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.permission.PermissionState
import li.songe.gkd.permission.appOpsRestrictStateList
import li.songe.gkd.ui.component.AuthButtonGroup
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.ManualAuthDialog
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AppOpsAllowPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AppOpsAllowVm>()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val appOpsRestricted by mainVm.appOpsRestrictedFlow.collectAsState()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                mainVm.popBackStack()
            })
        }, title = {
            Text(text = "解除限制")
        })
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            if (appOpsRestricted) {
                Column(
                    modifier = Modifier
                        .padding(itemHorizontalPadding, 0.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = "下列权限应默认授予，但可能因某些操作被限制",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        appOpsRestrictStateList.forEach { RestrictItem(it) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    AuthButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        buttons = listOf(
                            "Shizuku 授权" to vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                mainVm.guardShizukuContext()
                                toast("授权成功")
                            },
                            "外部授权" to {
                                vm.showCopyDlgFlow.value = true
                            },
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
            if (!appOpsRestricted) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                EmptyText(text = "状态正常, 无需操作")
            }
        }
    }

    val showCopyDlg by vm.showCopyDlgFlow.collectAsState()
    ManualAuthDialog(
        commandText = gkdStartCommandText,
        show = showCopyDlg,
        onUpdateShow = {
            vm.showCopyDlgFlow.value = it
        }
    )
}

@Composable
private fun RestrictItem(state: PermissionState) {
    if (!state.stateFlow.collectAsState().value) {
        Row {
            val lineHeightDp = LocalDensity.current.run { LocalTextStyle.current.lineHeight.toDp() }
            val size = 5.dp
            Spacer(
                modifier = Modifier
                    .padding(vertical = (lineHeightDp - size) / 2)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .size(size)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                style = MaterialTheme.typography.titleMedium,
                text = state.name,
            )
        }
    }
}
