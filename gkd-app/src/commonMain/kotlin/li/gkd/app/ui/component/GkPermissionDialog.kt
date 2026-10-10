package li.gkd.app.ui.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkPermissionDialog(
    title: String, message: String, confirmText: String, dismissText: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    GkAlertDialog(
        title = { Text(title) }, text = { Text(message) }, onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } })
}

@Composable
fun GkRestrictedSettingsDialog(onGrant: () -> Unit, onDismiss: () -> Unit) {
    GkPermissionDialog(
        stringResource(Res.string.permission_restricted),
        stringResource(Res.string.restricted_settings_permission_description),
        stringResource(Res.string.permission_go_grant),
        stringResource(Res.string.action_close),
        onGrant,
        onDismiss
    )
}

@Composable
fun GkAutomationOccupiedDialog(onDismiss: () -> Unit) {
    GkAlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(Res.string.startup_failed)) },
        text = { Text(stringResource(Res.string.automation_service_occupied_description)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_understood)) } })
}
