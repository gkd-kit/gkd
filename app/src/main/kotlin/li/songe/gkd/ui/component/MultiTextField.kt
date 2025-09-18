package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.MainActivity

@Composable
fun MultiTextField(
    modifier: Modifier = Modifier,
    textFlow: MutableStateFlow<String>,
    immediateFocus: Boolean = false,
    indicatorText: String? = null,
    placeholderText: String? = null,
) {
    val text by textFlow.collectAsState()
    Box(modifier = modifier) {
        val textColors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
            val modifier = Modifier
                .autoFocus(immediateFocus = immediateFocus)
                .fillMaxSize()
                .optimizedImePadding()
            TextField(
                value = text,
                onValueChange = { textFlow.value = it },
                placeholder = if (placeholderText != null) ({ Text(text = placeholderText) }) else null,
                modifier = modifier,
                shape = RectangleShape,
                colors = textColors,
            )
        }
        if (text.isNotEmpty()) {
            Text(
                text = indicatorText ?: text.length.toString(),
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopEnd)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}


private fun Modifier.optimizedImePadding() = composed {
    val context = LocalActivity.current as MainActivity
    if (context.imePlayingFlow.collectAsState().value) {
        this
    } else {
        imePadding()
    }
}