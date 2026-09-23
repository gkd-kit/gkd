package li.gkd.app.permission

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import li.gkd.app.ui.component.GkAlertDialog

class PermissionRequestContent(
    private val coordinator: PermissionRequestCoordinator,
    private val updateHostState: (resumed: Boolean, hasWindowFocus: Boolean) -> Unit,
    private val detachHost: () -> Unit,
) {
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
        var resumed by remember(lifecycle) {
            mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        DisposableEffect(lifecycle) {
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
            updateHostState(resumed, hasWindowFocus)
        }
    }

    @Composable
    private fun RequestDialog() {
        val state = coordinator.dialog.collectAsStateWithLifecycle().value
        if (state != null) {
            GkAlertDialog(
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
}
