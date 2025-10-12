package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import li.songe.gkd.MainActivity
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.updateAppMutex

@Composable
fun QueryPkgAuthCard(
    modifier: Modifier = Modifier,
) {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PerfIcon(
            imageVector = PerfIcon.WarningAmber,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "如需显示所有应用\n请授予「读取应用列表权限」",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(
            enabled = !updateAppMutex.state.collectAsState().value,
            onClick = throttle(fn = mainVm.viewModelScope.launchAsFn {
                requiredPermission(context, canQueryPkgState)
            })
        ) {
            Text(text = "申请权限")
        }
    }
}