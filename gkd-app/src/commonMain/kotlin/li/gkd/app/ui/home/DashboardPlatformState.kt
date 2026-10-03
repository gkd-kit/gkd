package li.gkd.app.ui.home

import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_fault
import li.gkd.app.resources.a11y_partially_disabled
import li.gkd.app.resources.a11y_running
import li.gkd.app.resources.a11y_stopped
import li.gkd.app.resources.a11y_unauthorized
import li.gkd.app.resources.automation_partially_disabled
import li.gkd.app.resources.automation_running
import li.gkd.app.resources.automation_stopped
import li.gkd.app.resources.automation_unauthorized
import li.gkd.app.priv.PrivilegeCapabilities
import li.gkd.app.permission.AppPermissionRestriction
import li.gkd.app.settings.SettingsStore
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.WorkModeRoute
import org.jetbrains.compose.resources.StringResource

/** Values supplied by Android services or the Desktop Android simulator. */
data class DashboardPlatformState(
    val a11yRunning: Boolean,
    val automationRunning: Boolean,
    val a11yEnabled: Boolean,
    val writeSecureSettings: Boolean,
    val partiallyDisabled: Boolean,
    val privilegeAvailable: Boolean,
    val privilegeStatus: DashboardPrivilegeStatus,
    val statusRunning: Boolean,
    val activityRunning: Boolean,
    val restricted: Boolean,
    val topAppId: String,
    val privilegeCapabilities: PrivilegeCapabilities? = null,
    val appRestrictions: Set<AppPermissionRestriction> = emptySet(),
) {
    fun usesA11y(store: SettingsStore, scope: Set<String>) =
        store.useA11y || (store.useAutomation && topAppId in scope)

    fun serviceEnabled(store: SettingsStore, scope: Set<String>) =
        if (usesA11y(store, scope)) a11yRunning else automationRunning

    fun subtitle(store: SettingsStore, scope: Set<String>): StringResource =
        if (usesA11y(store, scope)) {
            when {
                a11yRunning -> Res.string.a11y_running
                a11yEnabled -> Res.string.a11y_fault
                !writeSecureSettings -> Res.string.a11y_unauthorized
                store.enableAutomator && partiallyDisabled -> Res.string.a11y_partially_disabled
                else -> Res.string.a11y_stopped
            }
        } else {
            when {
                automationRunning -> Res.string.automation_running
                !privilegeAvailable -> Res.string.automation_unauthorized
                store.enableAutomator && partiallyDisabled -> Res.string.automation_partially_disabled
                else -> Res.string.automation_stopped
            }
        }

    fun authorizationRoute(enable: Boolean, store: SettingsStore, scope: Set<String>): AppRoute? =
        when {
            enable && usesA11y(store, scope) && !writeSecureSettings -> WorkModeRoute
            enable && !usesA11y(store, scope) &&
                    (!privilegeAvailable || privilegeCapabilities?.injectEvents == false) -> PrivilegeServiceRoute
            else -> null
        }
}

@androidx.compose.runtime.Composable
expect fun li.gkd.app.ui.navigation.AppWindow.dashboardPlatformState(): DashboardPlatformState
