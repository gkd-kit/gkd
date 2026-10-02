package li.gkd.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import priv.kit.ui.PrivilegeUiActions
import priv.kit.ui.PrivilegeUiRuntimeStartSource
import priv.kit.ui.PrivilegeUiRuntimeStatus
import priv.kit.ui.PrivilegeUiScreenState
import priv.kit.ui.PrivilegeUiServerRestartRequest
import priv.kit.ui.PrivilegeUiStartupMode
import priv.kit.ui.adb.PrivilegeUiStaticTcpSwitchAction

/** Executes simulated actions only. Every observable value belongs to SimulatorStore. */
class DesktopPrivilegeSimulation(
    private val scope: CoroutineScope,
    val store: SimulatorStore,
    private val copyText: (String) -> Unit,
    private val onFailure: (Throwable) -> Unit = { throw it },
) {
    private var operation: Job? = null
    private val current get() = store.settings.value.privilege
    val state: PrivilegeUiScreenState get() = store.settings.value.privilegeUiState()
    val externalAuthorizationRequested get() = current.externalAuthorizationRequested

    private fun command(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    private fun update(transform: (SimulatedPrivilege) -> SimulatedPrivilege) =
        store.updatePrivilege(transform = transform)

    private fun SimulatedPrivilege.logged(message: String) =
        copy(startupLogLines = (startupLogLines + "[simulation] $message").takeLast(80))

    val actions = PrivilegeUiActions(
        // These native-only prompts are never raised by the desktop simulator.
        continuePairingWithoutNotification = {},
        dismissTcpAuthorizationFailureDialog = {},
        openNotificationSettings = {},
        requestBatteryOptimization = { command(store::grantBatteryExemption); store.settings.value.permissions.ignoreBatteryOptimizations },
        requestLocalNetworkPermission = { command(store::grantLocalNetwork) },
        canInteract = { true },
        selectStartupMode = { mode ->
            command {
                update {
                    if (it.busy) it else it.copy(
                        selectedStartupMode = mode
                    )
                }
            }
        },
        startRoot = { command { requestStart(PrivilegeUiServerRestartRequest.Root) } },
        startWirelessAdb = { command { requestStart(PrivilegeUiServerRestartRequest.WirelessAdb) } },
        startStaticTcpAdb = { command { requestStart(PrivilegeUiServerRestartRequest.StaticTcpAdb) } },
        authorizeOrStartExternal = { id ->
            command {
                if (id == "simulation") requestStart(
                    PrivilegeUiServerRestartRequest.External(id)
                )
            }
        },
        startInteractive = {
            command {
                when (current.selectedStartupMode) {
                    PrivilegeUiStartupMode.ROOT -> requestStart(PrivilegeUiServerRestartRequest.Root)
                    PrivilegeUiStartupMode.ADB -> requestStart(PrivilegeUiServerRestartRequest.WirelessAdb)
                    PrivilegeUiStartupMode.EXTERNAL -> requestStart(
                        PrivilegeUiServerRestartRequest.External(
                            "simulation"
                        )
                    )

                    PrivilegeUiStartupMode.MANUAL_SHELL -> if (!current.busy && !current.available) beginStart(
                        null
                    )
                }
            }
        },
        confirmServerRestart = {
            command {
                current.restartConfirmationTarget?.let { request ->
                    update {
                        prepare(
                            it.cancelled()
                                .copy(runtimeStatus = PrivilegeUiRuntimeStatus.DISCONNECTED),
                            request
                        )
                    }
                    schedulePreparedStart()
                }
            }
        },
        cancelServerRestart = { command { update { it.copy(restartConfirmationTarget = null) } } },
        stopCurrentStart = ::cancelOperation,
        stopServer = {
            command {
                update { it.withAvailability(false).logged("Server stopped") }
                operation?.cancel()
            }
        },
        disableAutoRecovery = { command { update { it.copy(desiredEnabled = false) } } },
        startNotificationPairing = {
            command {
                update {
                    if (it.busy) it else it.copy(
                        busy = true,
                        pairingDialogVisible = true,
                        pairingCode = ""
                    )
                }
            }
        },
        stopNotificationPairing = ::cancelOperation,
        cancelPendingPairingStart = ::cancelOperation,
        updatePairingCode = { code ->
            command {
                update {
                    if (it.pairingInProgress) it else it.copy(pairingCode = code.filter { c -> c in '0'..'9' }
                        .take(6))
                }
            }
        },
        submitNotificationPairingCode = { command { submitPairingCode() } },
        confirmStaticTcpSwitch = { command { confirmTcpSwitch() } },
        cancelStaticTcpSwitch = ::cancelOperation,
        enableTcpMode = { command { requestTcpSwitch() } },
        restartTcpMode = { command { requestTcpSwitch() } },
        disableTcpMode = {
            command {
                update {
                    if (it.busy) it else it.disconnectAdb().copy(activeTcpPort = null)
                        .logged("Static TCP port stopped")
                }
            }
        },
        copyManualCommand = { state.manualShellCommandLine?.let(copyText) },
        copyStaticTcpCommand = { copyText("# Simulation only\nadb tcpip ${current.tcpPort}") },
        copyStartupLog = { copyText(current.startupLogLines.joinToString("\n")) },
        clearStartupLog = { command { update { it.copy(startupLogLines = emptyList()) } } },
    )

    private fun requestStart(request: PrivilegeUiServerRestartRequest) {
        if (current.busy || current.restartConfirmationTarget != null) return
        update {
            if (it.available) it.copy(restartConfirmationTarget = request) else prepare(
                it,
                request
            )
        }
        schedulePreparedStart()
    }

    private fun prepare(
        value: SimulatedPrivilege,
        request: PrivilegeUiServerRestartRequest
    ): SimulatedPrivilege = when {
        request == PrivilegeUiServerRestartRequest.WirelessAdb && !value.paired ->
            value.copy(
                pendingStart = request,
                busy = true,
                pairingDialogVisible = true,
                pairingCode = ""
            )

        request == PrivilegeUiServerRestartRequest.StaticTcpAdb && value.activeTcpPort == null ->
            value.copy(
                pendingStart = request,
                busy = true,
                staticTcpSwitchConfirmation = PrivilegeUiStaticTcpSwitchAction.START_SERVICE
            )

        request is PrivilegeUiServerRestartRequest.External && !value.externalAuthorized ->
            value.copy(pendingStart = request, busy = true, externalAuthorizationRequested = true)

        else -> starting(value, request)
    }

    private fun starting(
        value: SimulatedPrivilege,
        request: PrivilegeUiServerRestartRequest?
    ): SimulatedPrivilege {
        val source = when (request) {
            PrivilegeUiServerRestartRequest.Root -> PrivilegeUiRuntimeStartSource.ROOT
            PrivilegeUiServerRestartRequest.Adb, PrivilegeUiServerRestartRequest.WirelessAdb -> PrivilegeUiRuntimeStartSource.ADB_WIRELESS
            PrivilegeUiServerRestartRequest.StaticTcpAdb -> PrivilegeUiRuntimeStartSource.ADB_STATIC_TCP
            is PrivilegeUiServerRestartRequest.External -> PrivilegeUiRuntimeStartSource.EXTERNAL
            null -> null
        }
        return value.copy(
            operationId = value.operationId + 1, pendingStart = null, busy = true,
            runtimeStatus = PrivilegeUiRuntimeStatus.STARTING, runtimeStartSource = source,
            runtimeStartProviderId = (request as? PrivilegeUiServerRestartRequest.External)?.providerId
        ).logged("Starting ${source?.name ?: "MANUAL_SHELL"}")
    }

    private fun beginStart(request: PrivilegeUiServerRestartRequest?) {
        update { starting(it, request) }
        schedulePreparedStart()
    }

    private fun schedulePreparedStart() {
        if (current.runtimeStatus != PrivilegeUiRuntimeStatus.STARTING) return
        schedule(1_200, {
            it.copy(
                busy = false,
                runtimeStatus = PrivilegeUiRuntimeStatus.CONNECTED,
                desiredEnabled = it.runtimeStartSource != null,
                connectionSerial = it.connectionSerial + 1
            )
                .logged("Connected; uid=${if (it.runtimeStartSource == PrivilegeUiRuntimeStartSource.ROOT) SimulatorUid.ROOT_UID else SimulatorUid.SHELL_UID}")
        })
    }

    private fun schedule(
        delayMs: Long,
        next: (SimulatedPrivilege) -> SimulatedPrivilege,
        after: () -> Unit = {}
    ) {
        operation?.cancel()
        val id = current.operationId
        operation = scope.launch {
            delay(delayMs)
            command {
                if (current.operationId == id) {
                    store.updatePrivilege(id, next)
                    after()
                }
            }
        }
    }

    private fun submitPairingCode() {
        if (!current.pairingDialogVisible || current.pairingInProgress ||
            current.pairingCode.length != 6 || !current.pairingCode.all { it in '0'..'9' }
        ) return
        update { it.copy(pairingInProgress = true, operationId = it.operationId + 1) }
        schedule(700, { value ->
            val ready = value.copy(
                busy = false, pairingDialogVisible = false, pairingCode = "",
                pairingInProgress = false, paired = true
            ).logged("Wireless ADB paired")
            ready.pendingStart?.let { starting(ready, it) } ?: ready
        }, ::schedulePreparedStart)
    }

    private fun requestTcpSwitch() {
        update {
            if (it.busy) it else it.copy(
                busy = true,
                staticTcpSwitchConfirmation = PrivilegeUiStaticTcpSwitchAction.ENABLE_PORT
            )
        }
    }

    private fun confirmTcpSwitch() {
        if (current.staticTcpSwitchConfirmation == null) return
        update {
            it.disconnectAdb()
                .copy(staticTcpSwitchConfirmation = null, operationId = it.operationId + 1)
        }
        schedule(700, { value ->
            val ready = value.copy(busy = false, activeTcpPort = value.tcpPort)
                .logged("Static TCP port ready: ${value.tcpPort}")
            ready.pendingStart?.let { starting(ready, it) } ?: ready
        }, ::schedulePreparedStart)
    }

    fun confirmExternalAuthorization() = command {
        if (current.externalAuthorizationRequested) {
            val request = current.pendingStart ?: return@command
            update {
                starting(
                    it.copy(
                        externalAuthorizationRequested = false,
                        externalAuthorized = true
                    ), request
                )
            }
            schedulePreparedStart()
        }
    }

    fun cancelOperation() = command {
        update {
            if (it.busy || it.restartConfirmationTarget != null) it.cancelled()
                .logged("Operation cancelled") else it.cancelled()
        }
        operation?.cancel()
    }

    private fun SimulatedPrivilege.disconnectAdb() =
        if (serverUid == SimulatorUid.SHELL_UID) copy(runtimeStatus = PrivilegeUiRuntimeStatus.DISCONNECTED) else this

    fun close() {
        operation?.cancel()
    }
}
