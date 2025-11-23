package li.songe.gkd.ui.component

import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

@Composable
fun PerfTooltipBox(tooltipText: String?, content: @Composable () -> Unit) {
    if (tooltipText.isNullOrEmpty()) {
        content()
    } else {
        TooltipBox(
            tooltip = { PlainTooltip { Text(text = tooltipText) } },
            state = rememberTooltipState(),
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Start
            ),
            content = content,
        )
    }
}