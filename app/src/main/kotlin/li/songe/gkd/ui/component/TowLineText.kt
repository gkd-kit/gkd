package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TowLineText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    showApp: Boolean = false,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = title,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.MiddleEllipsis,
            style = MaterialTheme.typography.titleMedium,
        )
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleSmall) {
            if (showApp) {
                AppNameText(appId = subtitle)
            } else {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
    }
}
