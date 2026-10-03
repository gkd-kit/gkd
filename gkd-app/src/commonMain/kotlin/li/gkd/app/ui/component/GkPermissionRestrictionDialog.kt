package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.gkd.app.permission.AppPermissionRestriction
import li.gkd.app.priv.PrivilegeCapabilities
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.adb_permission_restricted
import li.gkd.app.resources.app_permission_restricted
import li.gkd.app.resources.automation_privilege_required_description
import li.gkd.app.resources.privilege_permissions_unverified
import li.gkd.app.resources.privilege_service_required
import li.gkd.app.resources.restriction_adb_app_ops
import li.gkd.app.resources.restriction_adb_footer
import li.gkd.app.resources.restriction_adb_grant
import li.gkd.app.resources.restriction_adb_input
import li.gkd.app.resources.restriction_adb_intro
import li.gkd.app.resources.restriction_adb_secure
import li.gkd.app.resources.restriction_app_accessibility
import li.gkd.app.resources.restriction_app_footer
import li.gkd.app.resources.restriction_app_foreground
import li.gkd.app.resources.restriction_app_intro
import li.gkd.app.resources.restriction_app_settings
import li.gkd.app.resources.restriction_details_go
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkPermissionRestrictionDialog(
    privilegeAvailable: Boolean,
    capabilities: PrivilegeCapabilities?,
    appRestrictions: Set<AppPermissionRestriction>,
    onDismiss: () -> Unit,
    onPrivilege: () -> Unit,
) {
    val adbRestricted = privilegeAvailable && capabilities?.restricted == true
    val appRestricted = !privilegeAvailable && appRestrictions.isNotEmpty()
    val items = buildList<StringResource> {
        if (adbRestricted) {
            if (!capabilities.injectEvents) add(Res.string.restriction_adb_input)
            if (!capabilities.grantRuntimePermissions) add(Res.string.restriction_adb_grant)
            if (!capabilities.writeSecureSettings) add(Res.string.restriction_adb_secure)
            if (!capabilities.updateAppOps) add(Res.string.restriction_adb_app_ops)
        } else if (appRestricted) {
            appRestrictions.forEach {
                add(when (it) {
                    AppPermissionRestriction.Accessibility -> Res.string.restriction_app_accessibility
                    AppPermissionRestriction.RestrictedSettings -> Res.string.restriction_app_settings
                    AppPermissionRestriction.ForegroundService -> Res.string.restriction_app_foreground
                })
            }
        }
    }
    GkAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(when {
                adbRestricted -> Res.string.adb_permission_restricted
                appRestricted -> Res.string.app_permission_restricted
                else -> Res.string.privilege_service_required
            }))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(when {
                    adbRestricted -> Res.string.restriction_adb_intro
                    appRestricted -> Res.string.restriction_app_intro
                    privilegeAvailable && capabilities == null -> Res.string.privilege_permissions_unverified
                    else -> Res.string.automation_privilege_required_description
                }))
                if (items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { Text("• " + stringResource(it)) }
                    }
                    Text(stringResource(if (adbRestricted) Res.string.restriction_adb_footer else Res.string.restriction_app_footer))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onPrivilege()
            }) { Text(stringResource(Res.string.restriction_details_go)) }
        },
    )
}
