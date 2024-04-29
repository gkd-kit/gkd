package li.songe.gkd.permission

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class AuthReason(
    val text: String,
    val confirm: () -> Unit
)

val authReasonFlow = MutableStateFlow<AuthReason?>(null)

@Composable
fun AuthDialog() {
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
                TextButton(onClick = {
                    authReasonFlow.value = null
                    authAction.confirm()
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

suspend fun asyncRequestPermission(
    context: Activity,
    permission: String,
): PermissionResult {
    if (XXPermissions.isGranted(context, permission)) {
        return PermissionResult.Granted
    }
    return suspendCoroutine { continuation ->
        XXPermissions.with(context)
            .unchecked()
            .permission(permission)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) {
                        continuation.resume(PermissionResult.Granted)
                    } else {
                        continuation.resume(PermissionResult.Denied(false))
                    }
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    continuation.resume(PermissionResult.Denied(doNotAskAgain))
                }
            })
    }
}

suspend fun checkOrRequestPermission(
    context: Activity,
    permissionState: PermissionState
): Boolean {
    if (!permissionState.updateAndGet()) {
        val result = permissionState.request?.let { it(context) } ?: return false
        if (result is PermissionResult.Denied) {
            if (result.doNotAskAgain) {
                authReasonFlow.value = permissionState.reason
            }
            return false
        }
    }
    return true
}
