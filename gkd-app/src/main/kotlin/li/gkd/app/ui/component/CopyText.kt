package li.songe.gkd.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import li.songe.gkd.util.copyText
import li.songe.gkd.util.throttle

@Composable
fun CopyableText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    textToCopy: String = text.text,
    contentPadding: PaddingValues = PaddingValues.Zero,
    textStyle: TextStyle = LocalTextStyle.current,
    contentColor: Color = LocalContentColor.current,
    textContentDescription: String? = null,
) {
    val textFieldState = remember(text.text) { TextFieldState(text.text) }
    val scrollState = rememberScrollState()
    val outputTransformation = remember(text) {
        if (text.spanStyles.isEmpty() && text.paragraphStyles.isEmpty()) {
            null
        } else {
            OutputTransformation {
                text.spanStyles.forEach { range ->
                    addStyle(range.item, range.start, range.end)
                }
                text.paragraphStyles.forEach { range ->
                    addStyle(range.item, range.start, range.end)
                }
            }
        }
    }
    CopyIconOverlay(
        textToCopy = textToCopy,
        copyIconVisible = textFieldState.selection.collapsed,
        contentColor = contentColor,
        modifier = modifier.defaultMinSize(
            minHeight = LocalMinimumInteractiveComponentSize.current,
        ),
    ) {
        BasicTextField(
            state = textFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .run {
                    if (textContentDescription == null) {
                        this
                    } else {
                        clearAndSetSemantics {
                            contentDescription = textContentDescription
                        }
                    }
            },
            readOnly = true,
            textStyle = textStyle.copy(color = contentColor),
            outputTransformation = outputTransformation,
            cursorBrush = SolidColor(Color.Unspecified),
            scrollState = scrollState,
        )
    }
}

@Composable
fun CopyIconOverlay(
    textToCopy: String,
    copyIconVisible: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()
        AnimatedVisibility(
            visible = copyIconVisible,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            PerfIconButton(
                imageVector = PerfIcon.ContentCopy,
                onClick = throttle { copyText(textToCopy) },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = contentColor.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

@Composable
fun CopyTextCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraSmall
    CopyableText(
        text = remember(text) { AnnotatedString(text) },
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(8.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
