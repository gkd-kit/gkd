package li.gkd.app.ui.platform

import androidx.compose.runtime.Composable
import li.gkd.app.platform.PlatformResult

@Composable expect fun privilegeAvailable(): Boolean

@Composable expect fun ignoresBatteryOptimizations(): Boolean

@Composable expect fun RefreshPermissions()

expect fun openAppDetails()

/** Success indicates whether a launcher activity was found and handed to the OS. */
expect fun UiHost.launchApp(appId: String): PlatformResult<Boolean>

expect suspend fun openRecents()

expect fun openA11ySettings()

expect fun switchAutomator()

expect fun dynamicColorAvailable(): Boolean
