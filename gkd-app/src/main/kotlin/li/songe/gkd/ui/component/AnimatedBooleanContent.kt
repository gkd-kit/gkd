package li.songe.gkd.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import li.songe.gkd.util.getUpDownTransform

@Composable
fun AnimatedBooleanContent(
    targetState: Boolean,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<Boolean>.() -> ContentTransform = { getUpDownTransform() },
    contentAlignment: Alignment = Alignment.TopStart,
    contentTrue: @Composable () -> Unit,
    contentFalse: @Composable () -> Unit,
) = AnimatedContent(
    targetState = targetState,
    modifier = modifier,
    transitionSpec = transitionSpec,
    contentAlignment = contentAlignment,
) {
    if (it) {
        contentTrue()
    } else {
        contentFalse()
    }
}