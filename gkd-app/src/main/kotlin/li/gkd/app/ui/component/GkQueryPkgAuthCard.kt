package li.gkd.app.ui.component

import li.gkd.app.MainViewModel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import li.gkd.app.text.UiStrings
import li.gkd.app.permission.PermissionStates
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.data.appinfo.AppInfoRepository

@Composable
fun GkQueryPkgAuthCard(
    modifier: Modifier = Modifier,
) {
    val mainVm = MainViewModel.requireCurrent()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GkIcon(
            imageVector = GkIcons.WarningAmber,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = UiStrings.query_apps_permission_notice,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(
            enabled = !AppInfoRepository.updating.collectAsStateWithLifecycle().value,
            onClick = throttle(fn = mainVm.scope.launchUiAction {
                mainVm.permissionRequests.ensurePermissions(PermissionStates.queryPackages)
            })
        ) {
            Text(text = UiStrings.permission_request)
        }
    }
}
