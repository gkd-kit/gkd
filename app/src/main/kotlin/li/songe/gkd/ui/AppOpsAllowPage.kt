package li.songe.gkd.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.grantPermissionByShizuku
import li.songe.gkd.permission.foregroundServiceSpecialUseState
import li.songe.gkd.ui.component.AuthButtonGroup
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.ManualAuthDialog
import li.songe.gkd.ui.local.LocalNavController
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.cardHorizontalPadding
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.surfaceCardColors
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.runCommandByRoot
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AppOpsAllowPage() {
    val context = LocalActivity.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<AppOpsAllowVm>()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val foregroundServiceSpecialUse by foregroundServiceSpecialUseState.stateFlow.collectAsStateWithLifecycle()
    val restrictedCount = arrayOf(foregroundServiceSpecialUse).count { !it }
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
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
            AnimatedVisibility(visible = !foregroundServiceSpecialUse) {
                Card(
                    modifier = Modifier
                        .padding(itemHorizontalPadding, 0.dp)
                        .fillMaxWidth(),
                    onClick = { },
                    colors = surfaceCardColors,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = "权限「特殊用途的前台服务」已被限制"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = "此权限应默认授予, 但您可能执行某些操作导致被限制",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AuthButtonGroup(
                        onClickShizuku = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            context.grantPermissionByShizuku(appOpsCommand)
                            toast("授权成功")
                        },
                        onClickManual = {
                            vm.showCopyDlgFlow.value = true
                        },
                        onClickRoot = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            runCommandByRoot(appOpsCommand)
                            toast("授权成功")
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(EmptyHeight))
            AnimatedVisibility(visible = restrictedCount == 0) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                EmptyText(text = "状态正常, 无需操作")
            }
        }
    }

    val showCopyDlg by vm.showCopyDlgFlow.collectAsState()
    ManualAuthDialog(
        commandText = appOpsCommand,
        show = showCopyDlg,
        onUpdateShow = {
            vm.showCopyDlgFlow.value = it
        }
    )
}

private val appOpsCommand by lazy {
    "appops set ${META.appId} FOREGROUND_SERVICE_SPECIAL_USE allow"
}

