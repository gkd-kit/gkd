package li.gkd.app.ui.component

import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import li.gkd.app.ui.share.LocalIsTalkbackEnabled

@Composable
fun GkTooltipIconButtonBox(contentDescription: String?, content: @Composable () -> Unit) {
    // Visually impaired users use TalkBack to read contentDescription; Tooltips are not needed.
    if (contentDescription.isNullOrEmpty() || LocalIsTalkbackEnabled.current) {
        content()
    } else {
        TooltipBox(
            tooltip = { PlainTooltip { Text(text = contentDescription) } },
            state = rememberTooltipState(),
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Start
            ),
            content = content,
        )
    }
}