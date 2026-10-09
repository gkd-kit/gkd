package li.gkd.app

import li.gkd.app.util.copyText

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.resources.Res
import li.gkd.app.resources.permission_reauthorize_to_unrestrict
import li.gkd.app.resources.simulation_authorization_title
import li.gkd.app.resources.simulation_authorization_message
import li.gkd.app.resources.action_agree
import li.gkd.app.resources.action_cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.jetbrains.compose.resources.stringResource
import li.gkd.app.ui.component.GkPlatformWarningDialogs
import li.gkd.app.ui.component.GkTermsAcceptDialog
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.text.getSync

@Composable
fun DesktopOverlays(state: DesktopState, session: DesktopSession) {
    session.state.mainVm.shareLog.Render(session)
    session.state.mainVm.githubUpload.Render(onCopy = ::copyText)
    session.state.mainVm.updateStatus?.UpgradeDialog()
    val simulatorSettings by state.simulator.settings.collectAsStateWithLifecycle()
    val awaiting = SimulatedService.entries.firstOrNull {
        simulatorSettings.services.state(it).phase == ServicePhase.AwaitingAuthorization
    }
    if (awaiting != null) {
        val attempt = simulatorSettings.services.state(awaiting).attempt
        fun respond(type: ServiceEventType) = state.simulatorCommand {
            DesktopRuntime.requireCurrent().services.event(ServiceEvent(awaiting, type, attempt))
        }
        AlertDialog(
            onDismissRequest = { respond(ServiceEventType.Cancelled) },
            title = { Text(stringResource(Res.string.simulation_authorization_title, stringResource(awaiting.label()))) },
            text = { Text(stringResource(Res.string.simulation_authorization_message)) },
            confirmButton = { TextButton(onClick = { respond(ServiceEventType.Authorized) }) { Text(stringResource(Res.string.action_agree)) } },
            dismissButton = { TextButton(onClick = { respond(ServiceEventType.Cancelled) }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    val device = simulatorSettings.environment().android
    GkPlatformWarningDialogs(
        device.restrictedWarning || state.overlay == "restricted",
        device.serviceEnabled,
        state.mainVm.navigator.topRoute is PrivilegeServiceRoute,
        device.automationOccupied || state.overlay == "occupied",
        {
            state.overlay = null; state.simulatorCommand {
            state.simulator.updateAndroid {
                it.copy(
                    restrictedWarning = false
                )
            }
        }
        },
        { state.mainVm.navigator.navigate(PrivilegeServiceRoute) },
        { state.toast.show(Res.string.permission_reauthorize_to_unrestrict.getSync()) },
        {
            state.overlay = null; state.simulatorCommand {
            state.simulator.updateAndroid {
                it.copy(
                    automationOccupied = false
                )
            }
        }
        })
    when (state.overlay) {
        "terms" -> GkTermsAcceptDialog(
            { state.overlay = null },
            { state.toast.show(it.message ?: it.toString()) },
            { state.overlay = null })
    }
}
