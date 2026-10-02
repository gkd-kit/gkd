package li.gkd.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var revision by mutableLongStateOf(0)
        private set

    fun show(text: String) {
        message = text; revision++
    }

    fun dismiss(expectedRevision: Long) {
        if (revision == expectedRevision) message = null
    }
}

/** A non-modal overlay: it neither takes focus nor consumes pointer input. */
@Composable
fun GkToastHost(
    state: ToastState, modifier: Modifier = Modifier,
    overlayHost: @Composable (@Composable () -> Unit) -> Unit = { content ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { content() }
    },
) {
    val revision = state.revision
    var displayedMessage by remember { mutableStateOf("") }
    if (state.message != null) displayedMessage = state.message.orEmpty()
    LaunchedEffect(revision, state.message) {
        if (state.message != null) {
            delay(2500); state.dismiss(revision)
        }
    }
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = state.message != null
    if (visible.currentState || visible.targetState) overlayHost {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp)
                    .background(
                        MaterialTheme.colorScheme.inverseSurface,
                        MaterialTheme.shapes.extraLarge
                    )
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    displayedMessage, Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}
