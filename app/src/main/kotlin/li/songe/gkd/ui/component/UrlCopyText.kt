package li.songe.gkd.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import li.songe.gkd.util.copyText
import li.songe.gkd.util.throttle


@Composable
fun UrlCopyText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        SelectionContainer(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Icon(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable(onClick = throttle {
                    copyText(text)
                })
                .padding(4.dp)
                .size(24.dp),
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
        )
    }
}