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
    onClickShizuku: () -> Unit,
    onClickManual: () -> Unit,
    onClickRoot: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .padding(4.dp, 0.dp)
            .fillMaxWidth(),
    ) {
        TextButton(onClick = throttle(onClickShizuku)) {
            Text(
                text = "Shizuku授权",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        TextButton(onClick = throttle(onClickManual)) {
            Text(
                text = "手动授权",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        TextButton(onClick = throttle(onClickRoot)) {
            Text(
                text = "ROOT授权",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}