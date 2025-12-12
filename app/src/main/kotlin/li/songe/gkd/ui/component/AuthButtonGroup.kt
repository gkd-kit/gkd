package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.gkd.util.throttle

@Composable
fun AuthButtonGroup(
    buttons: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
        .padding(4.dp, 0.dp)
        .fillMaxWidth()
) {
    FlowRow(
        modifier = modifier,
    ) {
        buttons.forEach { (text, click) ->
            TextButton(onClick = throttle(click)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}