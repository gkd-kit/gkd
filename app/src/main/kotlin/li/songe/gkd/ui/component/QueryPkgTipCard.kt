package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import li.songe.gkd.MainActivity
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.mayQueryPkgNoAccessFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.updateAppMutex

@Composable
fun QueryPkgAuthCard() {
    val canQueryPkg by canQueryPkgState.stateFlow.collectAsState()
    val mayQueryPkgNoAccess by mayQueryPkgNoAccessFlow.collectAsState()
    val appRefreshing by updateAppMutex.state.collectAsState()
    if (appRefreshing) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(EmptyHeight / 2))
            CircularProgressIndicator()
        }
    } else if (!canQueryPkg || mayQueryPkgNoAccess) {
        val mainVm = LocalMainViewModel.current
        val context = LocalActivity.current as MainActivity
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (!canQueryPkg) "如需显示所有应用\n请授予「读取应用列表权限」" else "检测到当前用户应用数量过少\n可尝试授予「读取应用列表权限」\n或关闭权限后重新授权",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = throttle(fn = mainVm.viewModelScope.launchAsFn {
                if (!canQueryPkg) {
                    requiredPermission(context, canQueryPkgState)
                } else {
                    canQueryPkgState.reason?.confirm?.invoke(context)
                }
            })) {
                Text(text = "申请权限")
            }
            Spacer(modifier = Modifier.height(EmptyHeight / 2))

        }
    }
}