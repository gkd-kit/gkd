package li.gkd.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Shared coordination; hosts provide real system events or explicit simulated events. */
@Composable
fun GkPlatformWarningDialogs(
    restrictedWarning: Boolean,
    a11yRunning: Boolean,
    privilegePage: Boolean,
    automationOccupied: Boolean,
    dismissRestricted: () -> Unit,
    openPrivilege: () -> Unit,
    reauthorizeNotice: () -> Unit,
    dismissOccupied: () -> Unit,
) {
    LaunchedEffect(restrictedWarning, a11yRunning, privilegePage) {
        if (restrictedWarning && (a11yRunning || privilegePage)) {
            if (!a11yRunning && privilegePage) reauthorizeNotice()
            dismissRestricted()
        }
    }
    if (restrictedWarning && !a11yRunning && !privilegePage) {
        GkRestrictedSettingsDialog({ dismissRestricted(); openPrivilege() }, dismissRestricted)
    }
    if (automationOccupied) GkAutomationOccupiedDialog(dismissOccupied)
}
