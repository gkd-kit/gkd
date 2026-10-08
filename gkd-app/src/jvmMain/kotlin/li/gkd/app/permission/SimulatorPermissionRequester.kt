package li.gkd.app.permission

import li.gkd.app.SimulatorStore
import li.gkd.app.resources.Res
import li.gkd.app.resources.platform_action_unsupported
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString

class SimulatorPermissionRequester(private val simulator: SimulatorStore) : PermissionRequester {
    override suspend fun ensurePermissions(vararg permissions: AppPermission): Boolean {
        for (permission in permissions) {
            val granted = when (permission) {
                AppPermission.LocalNetwork -> simulator.settings.value.permissions.localNetworkGranted
                AppPermission.IgnoreBatteryOptimizations -> simulator.settings.value.permissions.ignoreBatteryOptimizations
                AppPermission.QueryPackages -> simulator.settings.value.permissions.canQueryPackages
            }
            if (!granted) {
                ToastUtils.show(getString(Res.string.platform_action_unsupported))
                return false
            }
        }
        return true
    }
}
