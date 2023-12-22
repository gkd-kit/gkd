package li.songe.gkd.util

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.app

data class AuthAction(
    val title: String,
    val text: String,
    val confirm: () -> Unit
)

private val notifAuthAction by lazy {
    AuthAction(
        title = "权限请求",
        text = "当前操作需要通知权限\n您需要前往[通知管理]打开此权限",
        confirm = {
            val intent = Intent()
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, app.applicationInfo.uid)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            app.startActivity(intent)
        }
    )
}

val canDrawOverlaysAuthAction by lazy {
    AuthAction(
        title = "权限请求",
        text = "当前操作需要悬浮窗权限\n您需要前往[显示在其它应用的上层]打开此权限",
        confirm = {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            app.startActivity(intent)
        }
    )
}

val authActionFlow = MutableStateFlow<AuthAction?>(null)

@Composable
fun AuthDialog() {
    val authAction = authActionFlow.collectAsState().value
    if (authAction != null) {
        AlertDialog(
            title = {
                Text(text = authAction.title)
            },
            text = {
                Text(text = authAction.text)
            },
            onDismissRequest = { authActionFlow.value = null },
            confirmButton = {
                TextButton(onClick = {
                    authActionFlow.value = null
                    authAction.confirm()
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { authActionFlow.value = null }) {
                    Text(text = "取消")
                }
            }
        )
    }
}

fun checkOrRequestNotifPermission(context: Activity): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_DENIED
    ) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                0 // TODO 如何感知 (adb shell pm grant)/appops 这类引起的授权变化?
            )
        } else {
            authActionFlow.value = notifAuthAction
        }
        return false
    } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        authActionFlow.value = notifAuthAction
        return false
    }
    return true
}