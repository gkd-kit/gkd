package li.gkd.app.priv

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.appScope
import li.gkd.app.priv.shizuku.GkdShizukuExternalStartProvider
import priv.kit.core.Privilege
import priv.kit.ui.PrivilegeUi
import priv.kit.ui.PrivilegeUiConfig

enum class PrivilegeServiceStatus {
    Connected,
    Disconnected,
    DisconnectedDesired,
}

val privilegeServiceStatusFlow by lazy {
    val serverState = Privilege.serverState
    val desiredEnabled = PrivilegeUi.desiredEnabled
    val resolve = { connected: Boolean, desired: Boolean ->
        when {
            connected -> PrivilegeServiceStatus.Connected
            desired -> PrivilegeServiceStatus.DisconnectedDesired
            else -> PrivilegeServiceStatus.Disconnected
        }
    }
    combine(serverState, desiredEnabled) { serverInfo, desired ->
        resolve(serverInfo != null, desired)
    }.stateIn(
        appScope,
        SharingStarted.Eagerly,
        resolve(serverState.value != null, desiredEnabled.value),
    )
}

val gkdPrivilegeUiConfig: PrivilegeUiConfig by lazy {
    PrivilegeUiConfig(
        externalStartProviders = listOf(
            GkdShizukuExternalStartProvider,
        ),
    )
}
