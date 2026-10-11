package li.gkd.app.ui.home

import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

enum class DashboardPrivilegeStatus(val labelResource: StringResource) {
    Disconnected(Res.string.privilege_service_state_disconnected),
    Connected(Res.string.privilege_service_state_connected),
    DisconnectedDesired(Res.string.privilege_service_state_lost),
}
