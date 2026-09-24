package li.gkd.app.permission

import li.gkd.app.text.UiStrings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PermissionRequests(
    private val navigateToPrivilegeService: () -> Unit,
) {
    private val coordinator = PermissionRequestCoordinator()
    private val hostCommands = PermissionHostCommands()
    private val content = PermissionRequestContent(
        coordinator = coordinator,
        updateHostState = ::updateHostState,
        detachHost = ::detachHost,
    )
    private val hostInteractive = MutableStateFlow(false)
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val foregroundRefreshJob = refreshScope.launch {
        hostInteractive.collectLatest { interactive ->
            if (interactive) {
                coroutineScope {
                    PermissionStates.all.filter {
                        it.recheckPolicy !== PermissionRecheckPolicy.Immediate
                    }.forEach { permissionState ->
                        launch {
                            permissionState.recheckPolicy.awaitGranted(
                                hostInteractive,
                                permissionState::refresh,
                            )
                        }
                    }
                }
            }
        }
    }
    private val requestMutex = Mutex()
    private var activeRequestJob: Job? = null
    private var disposed = false

    @Composable
    fun Render(modifier: Modifier = Modifier) {
        content.Render(modifier)
    }

    suspend fun ensurePermissions(
        vararg permissionStates: PermissionState,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (disposed) return@withContext false
        requestMutex.withLock {
            if (disposed) return@withLock false
            val requestJob = currentCoroutineContext().job
            activeRequestJob = requestJob
            try {
                for (permissionState in permissionStates) {
                    if (permissionState.refresh()) continue
                    val permission = permissionState.permission
                    if (permission == null) {
                        val resolution = permissionState.resolution ?: return@withLock false
                        if (coordinator.awaitResolution(permissionState)) {
                            if (resolution.navigateToPrivilegeService) {
                                navigateToPrivilegeService()
                            }
                        }
                        return@withLock false
                    }
                    hostCommands.requestPermission(
                        permission = permission,
                        prompt = PermissionPrompt(
                            title = UiStrings.permission_request_progress(permissionState.name),
                            message = checkNotNull(permissionState.purpose) {
                                "${permissionState.name} is missing a permission request description"
                            },
                        ),
                    )
                    if (refreshAfterReturn(permissionState)) continue
                    if (!coordinator.awaitResolution(permissionState)) {
                        return@withLock false
                    }
                    hostCommands.openPermissionSettings(permission)
                    if (!refreshAfterReturn(permissionState)) {
                        return@withLock false
                    }
                }
                true
            } finally {
                if (activeRequestJob === requestJob) {
                    activeRequestJob = null
                }
            }
        }
    }

    private suspend fun refreshAfterReturn(permissionState: PermissionState): Boolean =
        permissionState.recheckPolicy.awaitGranted(hostInteractive, permissionState::refresh)

    private fun updateHostState(
        resumed: Boolean,
        hasWindowFocus: Boolean,
    ) {
        hostInteractive.value = resumed && hasWindowFocus
        coordinator.updateHostState(resumed, hasWindowFocus)
        hostCommands.updateHostState(resumed, hasWindowFocus)
    }

    private fun detachHost() {
        hostInteractive.value = false
        coordinator.updateHostState(resumed = false, hasWindowFocus = false)
        hostCommands.detachHost()
    }

    private fun dispose() {
        disposed = true
        refreshScope.cancel()
        activeRequestJob?.cancel()
        activeRequestJob = null
        hostCommands.dispose()
        coordinator.dispose()
    }

    class Host(activity: ComponentActivity) {
        private val delegate = PermissionRequestHost(activity)

        fun bind(requests: PermissionRequests) {
            delegate.bind(
                commands = requests.hostCommands,
                coordinator = requests.coordinator,
                onDetachHost = requests::detachHost,
                onDispose = requests::dispose,
            )
        }
    }
}
