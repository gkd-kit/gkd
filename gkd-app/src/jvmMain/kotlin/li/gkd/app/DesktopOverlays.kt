package li.gkd.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.resources.Res
import li.gkd.app.resources.logs_title
import li.gkd.app.resources.permission_reauthorize_to_unrestrict
import li.gkd.app.resources.upload_busy
import li.gkd.app.ui.component.GkPlatformWarningDialogs
import li.gkd.app.ui.component.GkShareLogDialog
import li.gkd.app.ui.component.GkTermsAcceptDialog
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.text.getSync
import li.gkd.app.ui.upload.GithubUploadItem

@Composable
fun DesktopOverlays(state: DesktopState, session: DesktopSession) {
    fun uploadLogs() {
        session.showShareLogs = false
        if (!session.githubUpload.startTask(
                GithubUploadItem(
                    Res.string.logs_title.getSync(), session.runtime::exportLogs,
                    releaseFile = { it.delete() }, showHref = { "http://i.gkd.li/log/${it.id}" },
                )
            )
        ) state.toast.show(Res.string.upload_busy.getSync())
    }
    if (session.showShareLogs) GkShareLogDialog(
        { session.showShareLogs = false },
        { session.showShareLogs = false; session.exportLogs(share = true) },
        { session.showShareLogs = false; session.exportLogs(save = true) }, ::uploadLogs
    )
    session.githubUpload.Render()
    session.updateStatus.UpgradeDialog()
    val simulatorSettings by state.simulator.settings.collectAsStateWithLifecycle()
    val device = simulatorSettings.environment().android
    GkPlatformWarningDialogs(
        device.restrictedWarning || state.overlay == "restricted",
        device.serviceEnabled,
        state.backStack.last() is PrivilegeServiceRoute,
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
        { state.navigate(PrivilegeServiceRoute) },
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
