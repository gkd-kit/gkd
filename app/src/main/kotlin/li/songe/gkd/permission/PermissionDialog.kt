package li.songe.gkd.permission

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.MainActivity

data class AuthReason(
    val text: () -> String,
    val confirm: ((Activity) -> Unit)? = null,
)

@Composable
fun AuthDialog(authReasonFlow: MutableStateFlow<AuthReason?>) {
    val authAction = authReasonFlow.collectAsState().value
    val context = LocalActivity.current as MainActivity
    if (authAction != null) {
        AlertDialog(
            title = {
                Text(text = "权限请求")
            },
            text = {
                Text(text = authAction.text())
            },
            onDismissRequest = { authReasonFlow.value = null },
            confirmButton = {
                TextButton(onClick = {
                    authReasonFlow.value = null
                    authAction.confirm?.invoke(context)
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { authReasonFlow.value = null }) {
                    Text(text = "取消")
                }
            }
        )
    }
}

sealed class PermissionResult {
    data object Granted : PermissionResult()
    data class Denied(val doNotAskAgain: Boolean) : PermissionResult()
}

suspend fun ensurePermission(
    context: MainActivity,
    vararg permissionStates: PermissionState,
): Boolean {
    for (permissionState in permissionStates) {
        if (permissionState.updateAndGet()) continue
        when (val result = permissionState.request?.invoke(context)) {
            PermissionResult.Granted -> Unit
            null -> {
                context.mainVm.authReasonFlow.value = permissionState.reason
                return false
            }
            is PermissionResult.Denied -> {
                if (result.doNotAskAgain) {
                    context.mainVm.authReasonFlow.value = permissionState.reason
                }
                return false
            }
        }
    }
    return true
}
