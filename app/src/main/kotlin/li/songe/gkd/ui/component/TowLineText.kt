package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TowLineText(
    title: String,
    subtitle: String,
    showApp: Boolean = false,
) {
    Column {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium
        )
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleSmall) {
            if (showApp) {
                AppNameText(appId = subtitle)
            } else {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
