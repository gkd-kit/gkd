package li.songe.gkd.permission

import android.app.Activity
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hjq.permissions.OnPermissionDescription
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.songe.gkd.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val permissionRequestCoordinator = PermissionRequestCoordinator()
private val permissionRequestMutex = Mutex()

@Composable
fun PermissionRequestHost(modifier: Modifier = Modifier) {
    BindPermissionRequestHostLifecycle()
    PermissionRequestDialog()
    PermissionPromptOverlay(modifier)
}

@Composable
private fun BindPermissionRequestHostLifecycle() {
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
            permissionRequestCoordinator.detachHost()
        }
    }
    SideEffect {
        permissionRequestCoordinator.updateHostState(
            resumed = resumed,
            hasWindowFocus = hasWindowFocus,
        )
    }
}

@Composable
private fun PermissionRequestDialog() {
    val state = permissionRequestCoordinator.dialog.collectAsState().value
    state ?: return
    AlertDialog(
        title = {
            Text(text = state.title)
        },
        text = {
            Text(text = state.message)
        },
        onDismissRequest = {
            permissionRequestCoordinator.dismissDialog(state.id)
        },
        confirmButton = {
            TextButton(onClick = {
                permissionRequestCoordinator.confirmDialog(state.id)
            }) {
                Text(text = state.confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                permissionRequestCoordinator.dismissDialog(state.id)
            }) {
                Text(text = state.dismissText)
            }
        },
    )
}

@Composable
private fun PermissionPromptOverlay(modifier: Modifier) {
    val requestedPrompt by permissionRequestCoordinator.visiblePrompt.collectAsState()
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
    val currentPrompt = displayedPrompt ?: return

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
            PermissionPromptCard(currentPrompt)
        }
    }
}

@Composable
private fun PermissionPromptCard(prompt: PermissionPrompt) {
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

suspend fun ensurePermission(
    context: MainActivity,
    vararg permissionStates: PermissionState,
): Boolean = withContext(Dispatchers.Main.immediate) {
    permissionRequestMutex.withLock {
        for (permissionState in permissionStates) {
            if (permissionState.refresh()) continue
            val permission = permissionState.permission
            if (permission == null) {
                val resolution = permissionState.resolution ?: return@withLock false
                if (permissionRequestCoordinator.awaitResolution(permissionState)) {
                    resolution.confirm?.invoke()
                }
                return@withLock false
            }
            val prompt = PermissionPrompt(
                title = "正在申请「${permissionState.name}」",
                message = checkNotNull(permissionState.purpose) {
                    "${permissionState.name} 缺少权限请求说明"
                },
            )
            requestPermission(
                context = context,
                permission = permission,
                prompt = prompt,
            )
            if (permissionState.refresh()) continue
            if (!permissionRequestCoordinator.awaitResolution(permissionState)) {
                return@withLock false
            }
            openPermissionSettings(context, permission)
            if (!permissionState.refresh()) {
                return@withLock false
            }
        }
        true
    }
}

private suspend fun openPermissionSettings(
    context: MainActivity,
    permission: IPermission,
): Unit = suspendCancellableCoroutine { continuation ->
    runCatching {
        XXPermissions.startPermissionActivity(context, permission) { _, _ ->
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }.onFailure { error ->
        if (continuation.isActive) {
            continuation.resumeWithException(error)
        }
    }
}

private suspend fun requestPermission(
    context: MainActivity,
    permission: IPermission,
    prompt: PermissionPrompt,
): Unit = suspendCancellableCoroutine { continuation ->
    if (XXPermissions.isGrantedPermission(context, permission)) {
        continuation.resume(Unit)
        return@suspendCancellableCoroutine
    }
    val description = GkdPermissionDescription(
        prompt = prompt,
    )
    runCatching {
        XXPermissions.with(context)
            .unchecked()
            .permission(permission)
            .description(description)
            .request { _, _ ->
                if (!continuation.isActive) return@request
                continuation.resume(Unit)
            }
    }.onFailure { error ->
        if (continuation.isActive) {
            continuation.resumeWithException(error)
        }
    }
}

private class GkdPermissionDescription(
    private val prompt: PermissionPrompt,
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
        promptSession = permissionRequestCoordinator.beginPrompt(prompt)
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

    fun detachHost() {
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
