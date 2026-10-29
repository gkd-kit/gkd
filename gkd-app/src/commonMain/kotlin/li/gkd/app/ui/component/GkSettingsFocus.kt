package li.gkd.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun Modifier.gkSettingsFocus(requested: Boolean, onConsumed: () -> Unit): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var highlightRequest by remember { mutableStateOf<Any?>(null) }
    var highlighted by remember { mutableStateOf(false) }
    val consume by rememberUpdatedState(onConsumed)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(requested) {
        if (requested) {
            highlightRequest = Any()
            consume()
        }
    }
    LaunchedEffect(highlightRequest, lifecycle) {
        if (highlightRequest == null) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        try {
            requester.bringIntoView()
            repeat(2) {
                highlighted = true
                delay(400)
                highlighted = false
                delay(400)
            }
        } finally {
            highlighted = false
        }
    }
    val color by
        animateColorAsState(
            if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.background,
            animationSpec = tween(300),
            label = "Setting focus background",
        )
    return bringIntoViewRequester(requester).background(color)
}
