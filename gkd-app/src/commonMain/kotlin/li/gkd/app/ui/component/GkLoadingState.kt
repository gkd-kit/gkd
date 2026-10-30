package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Loading indicator below page controls, aligned with empty content. */
@Composable
fun GkLoadingState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = GkEmptyStateDefaults.HorizontalPadding,
                    top = GkEmptyStateDefaults.TopPadding,
                    end = GkEmptyStateDefaults.HorizontalPadding,
                    bottom = 24.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
