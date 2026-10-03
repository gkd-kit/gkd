package li.gkd.app

import li.gkd.app.permission.AndroidPermissions
import li.gkd.app.priv.PrivilegeCapabilities

import priv.kit.ui.PrivilegeUiAdbPairingStatus
import priv.kit.ui.PrivilegeUiAdbTcpAuthorizationStatus
import priv.kit.ui.PrivilegeUiExternalStartItemState
import priv.kit.ui.PrivilegeUiExternalStartSnapshot
import priv.kit.ui.PrivilegeUiManagedWirelessAdbStatus
import priv.kit.ui.PrivilegeUiPermissionRestrictionStatus
import priv.kit.ui.PrivilegeUiRuntimeStartPhase
import priv.kit.ui.PrivilegeUiRuntimeStatus
import priv.kit.ui.PrivilegeUiScreenState
import priv.kit.ui.PrivilegeUiStartupMode
import priv.kit.ui.PrivilegeUiStaticTcpState
import priv.kit.ui.PrivilegeUiWirelessAdbStatus

/** Pure projection: permissions and availability never have a second writable copy. */
fun SimulatorSettings.privilegeUiState(): PrivilegeUiScreenState {
    val p = privilege
    val denied = if (p.serverUid == SimulatorUid.SHELL_UID) permissions.deniedServerPermissions else emptySet()
    val restricted = denied.isNotEmpty()
    val directory = "/data/app/~~simulation/priv.kit.sample-simulation"
    return PrivilegeUiScreenState(
        startupModes = PrivilegeUiStartupMode.entries.toList(),
        localNetworkPermissionMissing = !permissions.localNetworkGranted,
        batteryOptimizationPromptVisible = !permissions.ignoreBatteryOptimizations,
        wifiConnected = device.wifi,
        wirelessAdbStatusLoaded = true,
        wirelessDebuggingStatus = PrivilegeUiWirelessAdbStatus.ON,
        wirelessPairingServiceStatus = PrivilegeUiWirelessAdbStatus.ON,
        wirelessPairingCheckStatus = if (p.paired) PrivilegeUiWirelessAdbStatus.ON else PrivilegeUiWirelessAdbStatus.OFF,
        managedWirelessAdbStatus = PrivilegeUiManagedWirelessAdbStatus.READY,
        adbKeyFingerprint = "D3:50:9A:42:70:1B:8C:EE:41:20:7F:68:34:AD:09:55",
        manualShellCommandLine = if (p.useLegacyPackaging) "adb shell $directory/lib/arm64/libprivkitstarter.so"
        else "adb shell /system/bin/linker64 '$directory/base.apk!/lib/arm64-v8a/libprivkitstarter.so'",
        runtimeStatus = p.runtimeStatus, runtimeStartSource = p.runtimeStartSource,
        runtimeStartProviderId = p.runtimeStartProviderId, serverUid = p.serverUid,
        connectionSerial = p.connectionSerial, desiredEnabled = p.desiredEnabled,
        selectedStartupMode = p.selectedStartupMode, busy = p.busy,
        runtimeStartPhase = if (p.runtimeStatus == PrivilegeUiRuntimeStatus.STARTING) PrivilegeUiRuntimeStartPhase.RUNNING else PrivilegeUiRuntimeStartPhase.IDLE,
        runtimeProgressText = if (p.runtimeStatus == PrivilegeUiRuntimeStatus.STARTING) "正在启动模拟特权服务…" else null,
        permissionRestrictionStatus = if (restricted) PrivilegeUiPermissionRestrictionStatus.RESTRICTED else PrivilegeUiPermissionRestrictionStatus.NOT_RESTRICTED,
        deniedServerPermissions = denied.sorted(),
        pairingCode = p.pairingCode, pairingDialogVisible = p.pairingDialogVisible,
        pairingText = if (p.pairingDialogVisible) "请输入任意六位数字完成模拟配对" else null,
        pairingStatus = when {
            p.pairingInProgress -> PrivilegeUiAdbPairingStatus.PAIRING
            p.pairingDialogVisible -> PrivilegeUiAdbPairingStatus.FOUND
            p.paired -> PrivilegeUiAdbPairingStatus.PAIRED
            else -> PrivilegeUiAdbPairingStatus.NOT_PAIRED
        },
        tcpPort = p.tcpPort,
        staticTcp = PrivilegeUiStaticTcpState(
            loaded = true,
            activePort = p.activeTcpPort,
            configuredPort = p.activeTcpPort,
            authorizationStatus = if (p.activeTcpPort != null) PrivilegeUiAdbTcpAuthorizationStatus.AUTHORIZED else PrivilegeUiAdbTcpAuthorizationStatus.UNKNOWN
        ),
        staticTcpSwitchConfirmation = p.staticTcpSwitchConfirmation,
        restartConfirmationTarget = p.restartConfirmationTarget,
        startupLogLines = p.startupLogLines,
        externalStartItems = listOf(
            PrivilegeUiExternalStartItemState(
                id = "simulation", label = "Shizuku（模拟）",
                snapshot = PrivilegeUiExternalStartSnapshot(
                    available = true,
                    uid = SimulatorUid.SHELL_UID,
                    version = 1,
                    authorized = p.externalAuthorized
                ), statusLoaded = true
            )
        ),
    )
}

fun SimulatorSettings.privilegeCapabilities(): PrivilegeCapabilities? {
    if (!privilege.available) return null
    val denied = if (privilege.serverUid == SimulatorUid.SHELL_UID) permissions.deniedServerPermissions else emptySet()
    return PrivilegeCapabilities(
        grantRuntimePermissions = AndroidPermissions.GRANT_RUNTIME_PERMISSIONS !in denied,
        injectEvents = AndroidPermissions.INJECT_EVENTS !in denied,
        writeSecureSettings = AndroidPermissions.WRITE_SECURE_SETTINGS !in denied,
        updateAppOps = AndroidPermissions.UPDATE_APP_OPS_STATS !in denied,
    )
}
