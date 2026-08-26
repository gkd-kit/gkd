package li.songe.gkd.permission

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.hjq.permissions.OnPermissionDescription
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.songe.gkd.ui.component.AppAlertDialog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PermissionRequests(
    private val navigateToPrivilegeService: () -> Unit,
) {
    private val coordinator = PermissionRequestCoordinator()
    private val requestMutex = Mutex()
    private var activeRequestJob: Job? = null
    private var disposed = false
    private var nextHostCommandId = 0L
    private var hostCommandContinuation: CancellableContinuation<Unit>? = null

    private val hostCommandFlow = MutableStateFlow<PendingHostCommand?>(null)

    @Composable
    fun Render(modifier: Modifier = Modifier) {
        BindHostLifecycle()
        RequestDialog()
        PromptOverlay(modifier)
    }

    @Composable
    private fun BindHostLifecycle() {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val hasWindowFocus = LocalWindowInfo.current.isWindowFocused
        var resumed by remember {
            mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, _ ->
                resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                detachHost()
            }
        }
        SideEffect {
            updateHostState(
                resumed = resumed,
                hasWindowFocus = hasWindowFocus,
            )
        }
    }

    @Composable
    private fun RequestDialog() {
        val state = coordinator.dialog.collectAsStateWithLifecycle().value
        if (state != null) {
            AppAlertDialog(
                title = {
                    Text(text = state.title)
                },
                text = {
                    Text(text = state.message)
                },
                onDismissRequest = {
                    coordinator.dismissDialog(state.id)
                },
                confirmButton = {
                    TextButton(onClick = {
                        coordinator.confirmDialog(state.id)
                    }) {
                        Text(text = state.confirmText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        coordinator.dismissDialog(state.id)
                    }) {
                        Text(text = state.dismissText)
                    }
                },
            )
        }
    }

    @Composable
    private fun PromptOverlay(modifier: Modifier) {
        val requestedPrompt by coordinator.visiblePrompt.collectAsStateWithLifecycle()
        var prompt by remember { mutableStateOf<PermissionPrompt?>(null) }
        LaunchedEffect(requestedPrompt) {
            requestedPrompt?.let { delay(it.displayDelayMillis) }
            prompt = requestedPrompt
        }
        var displayedPrompt by remember { mutableStateOf(prompt) }
        val visibility = remember {
            MutableTransitionState(prompt != null)
        }
        LaunchedEffect(prompt) {
            if (prompt != null) {
                displayedPrompt = prompt
            }
            visibility.targetState = prompt != null
        }
        LaunchedEffect(
            prompt,
            visibility.currentState,
            visibility.isIdle,
        ) {
            if (prompt == null && visibility.isIdle && !visibility.currentState) {
                displayedPrompt = null
            }
        }
        val currentPrompt = displayedPrompt
        if (currentPrompt != null) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    PromptCard(currentPrompt)
                }
            }
        }
    }

    @Composable
    private fun PromptCard(prompt: PermissionPrompt) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = prompt.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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
                    val prompt = PermissionPrompt(
                        title = "正在申请「${permissionState.name}」",
                        message = checkNotNull(permissionState.purpose) {
                            "${permissionState.name} 缺少权限请求说明"
                        },
                    )
                    awaitHostCommand(
                        RequestPermissionCommand(
                            id = nextHostCommandId(),
                            permission = permission,
                            prompt = prompt,
                        ),
                    )
                    if (permissionState.refresh()) continue
                    if (!coordinator.awaitResolution(permissionState)) {
                        return@withLock false
                    }
                    awaitHostCommand(
                        OpenPermissionSettingsCommand(
                            id = nextHostCommandId(),
                            permission = permission,
                        ),
                    )
                    if (!permissionState.refresh()) {
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

    private fun nextHostCommandId(): Long = ++nextHostCommandId

    private suspend fun awaitHostCommand(command: HostCommand) {
        suspendCancellableCoroutine { continuation ->
            synchronized(this) {
                check(hostCommandFlow.value == null) { "已有权限平台请求正在执行" }
                hostCommandContinuation = continuation
                hostCommandFlow.value = PendingHostCommand(command = command)
            }
            continuation.invokeOnCancellation {
                synchronized(this) {
                    if (hostCommandContinuation === continuation) {
                        hostCommandContinuation = null
                        hostCommandFlow.value = null
                    }
                }
            }
        }
    }

    private fun takeHostCommandToLaunch(): HostCommand? = synchronized(this) {
        val pending = hostCommandFlow.value ?: return@synchronized null
        if (pending.launched) return@synchronized null
        hostCommandFlow.value = pending.copy(launched = true)
        pending.command
    }

    private fun completeHostCommand(id: Long) {
        val continuation = synchronized(this) {
            val pending = hostCommandFlow.value
            if (pending?.command?.id != id) return
            hostCommandFlow.value = null
            hostCommandContinuation.also { hostCommandContinuation = null }
        }
        if (continuation?.isActive == true) {
            continuation.resume(Unit)
        }
    }

    private fun failHostCommand(id: Long, error: Throwable) {
        val continuation = synchronized(this) {
            val pending = hostCommandFlow.value
            if (pending?.command?.id != id) return
            hostCommandFlow.value = null
            hostCommandContinuation.also { hostCommandContinuation = null }
        }
        if (continuation?.isActive == true) {
            continuation.resumeWithException(error)
        }
    }

    private fun updateHostState(
        resumed: Boolean,
        hasWindowFocus: Boolean,
    ) {
        coordinator.updateHostState(resumed, hasWindowFocus)
        val returnedCommandId = synchronized(this) {
            val pending = hostCommandFlow.value ?: return@synchronized null
            if (pending.launched && (!resumed || !hasWindowFocus)) {
                hostCommandFlow.value = pending.copy(leftHost = true)
                null
            } else if (pending.launched && pending.leftHost) {
                pending.command.id
            } else {
                null
            }
        }
        returnedCommandId?.let(::completeHostCommand)
    }

    private fun detachHost() {
        coordinator.updateHostState(resumed = false, hasWindowFocus = false)
        synchronized(this) {
            val pending = hostCommandFlow.value
            if (pending?.launched == true) {
                hostCommandFlow.value = pending.copy(leftHost = true)
            }
        }
    }

    private fun dispose() {
        disposed = true
        activeRequestJob?.cancel()
        activeRequestJob = null
        val continuation = synchronized(this) {
            hostCommandFlow.value = null
            hostCommandContinuation.also { hostCommandContinuation = null }
        }
        continuation?.cancel()
        coordinator.dispose()
    }

    class Host(
        private val activity: ComponentActivity,
    ) : DefaultLifecycleObserver {
        private var requests: PermissionRequests? = null
        private var bindingJob: Job? = null

        init {
            activity.lifecycle.addObserver(this)
        }

        fun bind(requests: PermissionRequests) {
            if (this.requests === requests) return
            bindingJob?.cancel()
            this.requests = requests
            bindingJob = activity.lifecycleScope.launch {
                requests.hostCommandFlow.collect {
                    requests.takeHostCommandToLaunch()?.let(::execute)
                }
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            bindingJob?.cancel()
            bindingJob = null
            if (activity.isChangingConfigurations) {
                requests?.detachHost()
            } else {
                requests?.dispose()
            }
            requests = null
            owner.lifecycle.removeObserver(this)
        }

        private fun execute(command: HostCommand) {
            when (command) {
                is RequestPermissionCommand -> requestPermission(command)
                is OpenPermissionSettingsCommand -> openPermissionSettings(command)
            }
        }

        private fun requestPermission(command: RequestPermissionCommand) {
            val requests = requests ?: return
            if (XXPermissions.isGrantedPermission(activity, command.permission)) {
                requests.completeHostCommand(command.id)
                return
            }
            val description = GkdPermissionDescription(
                prompt = command.prompt,
                coordinator = requests.coordinator,
            )
            runCatching {
                XXPermissions.with(activity)
                    .unchecked()
                    .permission(command.permission)
                    .description(description)
                    .request { _, _ ->
                        requests.completeHostCommand(command.id)
                    }
            }.onFailure { error ->
                requests.failHostCommand(command.id, error)
            }
        }

        private fun openPermissionSettings(command: OpenPermissionSettingsCommand) {
            val requests = requests ?: return
            runCatching {
                XXPermissions.startPermissionActivity(activity, command.permission) { _, _ ->
                    requests.completeHostCommand(command.id)
                }
            }.onFailure { error ->
                requests.failHostCommand(command.id, error)
            }
        }
    }

    private sealed interface HostCommand {
        val id: Long
    }

    private data class RequestPermissionCommand(
        override val id: Long,
        val permission: IPermission,
        val prompt: PermissionPrompt,
    ) : HostCommand

    private data class OpenPermissionSettingsCommand(
        override val id: Long,
        val permission: IPermission,
    ) : HostCommand

    private data class PendingHostCommand(
        val command: HostCommand,
        val launched: Boolean = false,
        val leftHost: Boolean = false,
    )

}

private class GkdPermissionDescription(
    private val prompt: PermissionPrompt,
    private val coordinator: PermissionRequestCoordinator,
) : OnPermissionDescription {
    private var promptSession: AutoCloseable? = null

    override fun askWhetherRequestPermission(
        activity: Activity,
        requestList: List<IPermission>,
        continueRequestRunnable: Runnable,
        breakRequestRunnable: Runnable,
    ) {
        continueRequestRunnable.run()
    }

    override fun onRequestPermissionStart(
        activity: Activity,
        requestList: List<IPermission>,
    ) {
        promptSession = coordinator.beginPrompt(prompt)
    }

    override fun onRequestPermissionEnd(
        activity: Activity,
        requestList: List<IPermission>,
    ) {
        promptSession?.close()
        promptSession = null
    }
}

private data class PermissionPrompt(
    val title: String,
    val message: String,
    val displayDelayMillis: Long = 500L,
)

private data class PermissionDialogState(
    val id: Long,
    val title: String,
    val message: String,
    val confirmText: String,
    val dismissText: String = "取消",
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

private class PermissionRequestCoordinator {
    private val lock = Any()
    private var activityResumed = false
    private var activityHasWindowFocus = false
    private var nextId = 0L
    private var activePrompt: ActivePrompt? = null

    val visiblePrompt = MutableStateFlow<PermissionPrompt?>(null)
    val dialog = MutableStateFlow<PermissionDialogState?>(null)

    fun updateHostState(
        resumed: Boolean,
        hasWindowFocus: Boolean,
    ) {
        synchronized(lock) {
            activityResumed = resumed
            activityHasWindowFocus = hasWindowFocus
            if (!resumed || !hasWindowFocus) {
                revealPromptLocked()
            }
            dismissReturnedPromptLocked()
        }
    }

    fun dispose() {
        val dialogToDismiss = synchronized(lock) {
            activityResumed = false
            activityHasWindowFocus = false
            activePrompt = null
            visiblePrompt.value = null
            dialog.value.also {
                dialog.value = null
            }
        }
        dialogToDismiss?.onDismiss?.invoke()
    }

    fun beginPrompt(prompt: PermissionPrompt): AutoCloseable {
        val id = synchronized(lock) {
            val newId = ++nextId
            activePrompt = ActivePrompt(
                id = newId,
                prompt = prompt,
            )
            visiblePrompt.value = null
            if (!activityResumed || !activityHasWindowFocus) {
                revealPromptLocked()
            }
            newId
        }
        return PromptSession(id)
    }

    suspend fun awaitResolution(permissionState: PermissionState): Boolean {
        val resolution = permissionState.resolution ?: return false
        return suspendCancellableCoroutine { continuation ->
            val id = showDialog(
                title = "权限请求",
                message = listOfNotNull(
                    permissionState.purpose,
                    resolution.message,
                ).joinToString("\n\n"),
                confirmText = resolution.confirmText,
                onConfirm = {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                },
                onDismiss = {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                },
            )
            continuation.invokeOnCancellation {
                dismissDialog(id)
            }
        }
    }

    fun confirmDialog(id: Long) {
        takeDialog(id)?.onConfirm?.invoke()
    }

    fun dismissDialog(id: Long) {
        takeDialog(id)?.onDismiss?.invoke()
    }

    private fun showDialog(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
    ): Long {
        val previousDialog: PermissionDialogState?
        val id: Long
        synchronized(lock) {
            previousDialog = dialog.value
            id = ++nextId
            val state = PermissionDialogState(
                id = id,
                title = title,
                message = message,
                confirmText = confirmText,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
            dialog.value = state
        }
        previousDialog?.onDismiss?.invoke()
        return id
    }

    private fun takeDialog(id: Long): PermissionDialogState? = synchronized(lock) {
        dialog.value?.takeIf { it.id == id }?.also {
            dialog.value = null
        }
    }

    private fun revealPromptLocked() {
        val prompt = activePrompt ?: return
        if (prompt.visible) return
        prompt.visible = true
        visiblePrompt.value = prompt.prompt
    }

    private fun dismissReturnedPromptLocked() {
        val prompt = activePrompt ?: return
        if (
            prompt.visible &&
            activityResumed &&
            (activityHasWindowFocus || prompt.completed)
        ) {
            clearPromptLocked()
        }
    }

    private fun finishPrompt(id: Long) {
        synchronized(lock) {
            val prompt = activePrompt ?: return
            if (prompt.id != id) return
            prompt.completed = true
            if (!prompt.visible) {
                clearPromptLocked()
            } else {
                dismissReturnedPromptLocked()
            }
        }
    }

    private fun clearPromptLocked() {
        activePrompt = null
        visiblePrompt.value = null
    }

    private inner class PromptSession(
        private val id: Long,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                finishPrompt(id)
            }
        }
    }

    private data class ActivePrompt(
        val id: Long,
        val prompt: PermissionPrompt,
        var visible: Boolean = false,
        var completed: Boolean = false,
    )
}
