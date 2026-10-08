package li.gkd.app

import li.gkd.app.util.copyText

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.resources.Res
import li.gkd.app.resources.permission_reauthorize_to_unrestrict
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
