package li.gkd.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import priv.kit.ui.PrivilegeUiRuntimeStartSource
import priv.kit.ui.PrivilegeUiRuntimeStatus
import priv.kit.ui.PrivilegeUiServerRestartRequest
import priv.kit.ui.PrivilegeUiStartupMode
import priv.kit.ui.adb.PrivilegeUiStaticTcpSwitchAction

val defaultSimulatedDevice = SimulatedDevice()

/** The only simulator snapshot. DesktopEnvironment projects inputs for window and page rendering. */
@Serializable
data class SimulatorSettings(
    val device: SimulatedDevice = defaultSimulatedDevice,
    val permissions: SimulatedPermissions = SimulatedPermissions(),
    val services: SimulatedServices = SimulatedServices(),
    val prompts: SimulatedPrompts = SimulatedPrompts(),
    val privilege: SimulatedPrivilege = SimulatedPrivilege(),
) {
    val networkAvailable: Boolean get() = device.wifi || device.mobileSignal > 0

    fun environment() = DesktopEnvironment(
        width = device.width, height = device.height, density = device.density,
        fontScale = device.fontScale, dark = device.dark, locale = device.locale,
        android = AndroidWindow(
            statusBarHeight = device.statusBarHeight,
            navigationBarHeight = device.navigationBarHeight,
            statusBarVisible = device.statusBarVisible,
            navigationBarVisible = device.navigationBarVisible,
            gestureHandleVisible = device.gestureHandleVisible,
            cutoutWidth = device.cutoutWidth,
            cutoutHeight = device.cutoutHeight,
            batteryPercent = device.batteryPercent,
            charging = device.charging,
            wifi = device.wifi,
            mobileSignal = device.mobileSignal,
            imeVisible = device.imeVisible,
            imeHeight = device.imeHeight,
            topAppId = device.topAppId,
            localNetworkGranted = permissions.localNetworkGranted,
            restricted = permissions.restricted,
            ignoreBatteryOptimizations = permissions.ignoreBatteryOptimizations,
            writeSecureSettings = permissions.writeSecureSettings,
            serviceEnabled = services.serviceEnabled,
            automationRunning = services.automationRunning,
            a11yEnabled = services.a11yEnabled,
            partiallyDisabled = services.partiallyDisabled,
            activityRunning = services.activityRunning,
            statusEnabled = services.statusEnabled,
            restrictedWarning = prompts.restrictedWarning,
            automationOccupied = prompts.automationOccupied,
            privilegeAvailable = privilege.available,
        ),
    )

    fun withEnvironment(value: DesktopEnvironment): SimulatorSettings {
        value.validate()
        val a = value.android
        return copy(
            device = SimulatedDevice(
                value.width,
                value.height,
                value.density,
                value.fontScale,
                value.dark,
                value.locale,
                a.statusBarHeight,
                a.navigationBarHeight,
                a.statusBarVisible,
                a.navigationBarVisible,
                a.gestureHandleVisible,
                a.cutoutWidth,
                a.cutoutHeight,
                a.batteryPercent,
                a.charging,
                a.wifi,
                a.mobileSignal,
                a.imeVisible,
                a.imeHeight,
                a.topAppId
            ),
            permissions = permissions.copy(
                restricted = a.restricted,
                localNetworkGranted = a.localNetworkGranted,
                ignoreBatteryOptimizations = a.ignoreBatteryOptimizations,
                writeSecureSettings = a.writeSecureSettings
            ),
            services = SimulatedServices(
                a.serviceEnabled, a.automationRunning, a.a11yEnabled,
                a.partiallyDisabled, a.activityRunning, a.statusEnabled
            ),
            prompts = SimulatedPrompts(a.restrictedWarning, a.automationOccupied),
            privilege = if (privilege.available == a.privilegeAvailable) privilege else privilege.withAvailability(
                a.privilegeAvailable
            ),
        )
    }

    fun persistent() = copy(
        device = device.copy(imeVisible = false, batteryPercent = defaultSimulatedDevice.batteryPercent),
        privilege = privilege.persistent()
    )

    fun validate() {
        environment().validate()
        require(privilege.tcpPort in 1..65535) { "Invalid TCP port" }
        require(privilege.activeTcpPort == null || privilege.activeTcpPort in 1..65535) { "Invalid active TCP port" }
    }
}

@Serializable
data class SimulatedDevice(
    val width: Int = 412,
    val height: Int = 820,
    val density: Float = 1f,
    val fontScale: Float = 1f,
    val dark: Boolean = false,
    val locale: String = "zh-CN",
    val statusBarHeight: Float = 24f,
    val navigationBarHeight: Float = 24f,
    val statusBarVisible: Boolean = true,
    val navigationBarVisible: Boolean = true,
    val gestureHandleVisible: Boolean = true,
    val cutoutWidth: Float = 0f,
    val cutoutHeight: Float = 0f,
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val wifi: Boolean = true,
    val mobileSignal: Int = 4,
    val imeVisible: Boolean = false,
    val imeHeight: Float = 280f,
    val topAppId: String = "",
)

@Serializable
data class SimulatedPermissions(
    val canQueryPackages: Boolean = true, val queryPackagesAbnormal: Boolean = false,
    val restricted: Boolean = false, val localNetworkGranted: Boolean = true,
    val ignoreBatteryOptimizations: Boolean = false, val writeSecureSettings: Boolean = false,
    val deniedServerPermissions: Set<String> = emptySet(),
)

@Serializable
data class SimulatedServices(
    val serviceEnabled: Boolean = false, val automationRunning: Boolean = false,
    val a11yEnabled: Boolean = false, val partiallyDisabled: Boolean = false,
    val activityRunning: Boolean = false, val statusEnabled: Boolean = false,
)

@Serializable
data class SimulatedPrompts(
    val restrictedWarning: Boolean = false,
    val automationOccupied: Boolean = false
)

@Serializable
data class SimulatedPrivilege(
    val runtimeStatus: PrivilegeUiRuntimeStatus = PrivilegeUiRuntimeStatus.DISCONNECTED,
    val runtimeStartSource: PrivilegeUiRuntimeStartSource? = null,
    val runtimeStartProviderId: String? = null,
    val desiredEnabled: Boolean = false,
    val selectedStartupMode: PrivilegeUiStartupMode = PrivilegeUiStartupMode.ADB,
    val paired: Boolean = false,
    val externalAuthorized: Boolean = false,
    val tcpPort: Int = 5555,
    val activeTcpPort: Int? = null,
    val useLegacyPackaging: Boolean = true,
    @Transient val operationId: Long = 0,
    @Transient val connectionSerial: Long = 0,
    @Transient val busy: Boolean = false,
    @Transient val pendingStart: PrivilegeUiServerRestartRequest? = null,
    @Transient val restartConfirmationTarget: PrivilegeUiServerRestartRequest? = null,
    @Transient val staticTcpSwitchConfirmation: PrivilegeUiStaticTcpSwitchAction? = null,
    @Transient val externalAuthorizationRequested: Boolean = false,
    @Transient val pairingDialogVisible: Boolean = false,
    @Transient val pairingCode: String = "",
    @Transient val pairingInProgress: Boolean = false,
    @Transient val startupLogLines: List<String> = emptyList(),
) {
    val available: Boolean get() = runtimeStatus == PrivilegeUiRuntimeStatus.CONNECTED
    val serverUid: Int? get() = if (!available) null else if (runtimeStartSource == PrivilegeUiRuntimeStartSource.ROOT) SimulatorUid.ROOT_UID else SimulatorUid.SHELL_UID

    fun cancelled() = copy(
        operationId = operationId + 1, busy = false, pendingStart = null,
        restartConfirmationTarget = null, staticTcpSwitchConfirmation = null,
        externalAuthorizationRequested = false, pairingDialogVisible = false,
        pairingCode = "", pairingInProgress = false,
        runtimeStatus = if (runtimeStatus == PrivilegeUiRuntimeStatus.STARTING) PrivilegeUiRuntimeStatus.DISCONNECTED else runtimeStatus,
    )

    fun withAvailability(value: Boolean) = cancelled().copy(
        runtimeStatus = if (value) PrivilegeUiRuntimeStatus.CONNECTED else PrivilegeUiRuntimeStatus.DISCONNECTED,
        runtimeStartSource = if (value) PrivilegeUiRuntimeStartSource.ADB_WIRELESS else null,
        runtimeStartProviderId = null, desiredEnabled = value,
    )

    fun persistent() =
        cancelled().copy(operationId = 0, connectionSerial = 0, startupLogLines = emptyList())
}
