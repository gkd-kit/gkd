package li.songe.gkd.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield
import li.songe.gkd.MainActivity
import kotlin.coroutines.coroutineContext

data class AuthReason(
    val text: String,
    val confirm: (() -> Unit)? = null,
    val renderConfirm: @Composable (() -> (() -> Unit))? = null,
)

@Composable
fun AuthDialog(authReasonFlow: MutableStateFlow<AuthReason?>) {
    val authAction = authReasonFlow.collectAsState().value
    if (authAction != null) {
        AlertDialog(
            title = {
                Text(text = "权限请求")
            },
            text = {
                Text(text = authAction.text)
            },
            onDismissRequest = { authReasonFlow.value = null },
            confirmButton = {
                val composeConfirm = authAction.renderConfirm?.invoke()
                TextButton(onClick = {
                    authReasonFlow.value = null
                    (composeConfirm ?: authAction.confirm)?.invoke()
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

private suspend fun checkOrRequestPermission(
    context: MainActivity,
    permissionState: PermissionState
): Boolean {
    if (!permissionState.updateAndGet()) {
        val result = permissionState.request?.invoke(context)
        if (result == null) {
            context.mainVm.authReasonFlow.value = permissionState.reason
            return false
        } else if (result is PermissionResult.Denied) {
            if (result.doNotAskAgain) {
                context.mainVm.authReasonFlow.value = permissionState.reason
            }
            return false
        }
    }
    return true
}

suspend fun requiredPermission(
    context: MainActivity,
    permissionState: PermissionState
) {
    val r = checkOrRequestPermission(context, permissionState)
    if (!r) {
        coroutineContext[Job]?.cancel()
        yield()
    }
}
