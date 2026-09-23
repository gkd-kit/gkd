package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import li.gkd.app.text.UiStrings

object GkEmptyStateDefaults {
    val TopPadding = 80.dp
    val HorizontalPadding = 24.dp
    val ActionSpacing = 16.dp
}

/** Place inside the content area, after Scaffold insets and any page controls. */
@Composable
fun GkEmptyState(
    text: String = UiStrings.data_empty,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = GkEmptyStateDefaults.HorizontalPadding,
                top = GkEmptyStateDefaults.TopPadding,
                end = GkEmptyStateDefaults.HorizontalPadding,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        if (action != null) {
            Spacer(Modifier.height(GkEmptyStateDefaults.ActionSpacing))
            action()
        }
    }
}
