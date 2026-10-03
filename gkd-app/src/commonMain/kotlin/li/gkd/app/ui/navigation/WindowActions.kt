package li.gkd.app.ui.navigation

import androidx.compose.runtime.Composable
import li.gkd.app.ui.option.AutomatorModeOption

@Composable
expect fun AppWindow.privilegeAvailable(): Boolean

@Composable
expect fun AppWindow.ignoresBatteryOptimizations(): Boolean
@Composable
expect fun AppWindow.RefreshPermissions()
expect fun AppWindow.setStatusServiceEnabled(enabled: Boolean)
expect fun AppWindow.requestIgnoreBatteryOptimizations()
expect fun AppWindow.openAppDetails()
expect fun AppWindow.openRecents()
expect fun AppWindow.openA11ySettings()
expect fun AppWindow.changeAutomatorMode(mode: AutomatorModeOption)
expect fun AppWindow.switchAutomator()
expect fun AppWindow.requestQueryPackages()
expect fun AppWindow.dynamicColorAvailable(): Boolean
