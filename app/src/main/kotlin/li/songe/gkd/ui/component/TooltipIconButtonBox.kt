package li.songe.gkd.ui.component

import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import li.songe.gkd.ui.share.LocalIsTalkbackEnabled

@Composable
fun TooltipIconButtonBox(contentDescription: String?, content: @Composable () -> Unit) {
    // 视障用户使用 TalkBack 朗读 contentDescription，不需要 Tooltip
    if (contentDescription.isNullOrEmpty() || LocalIsTalkbackEnabled.current.collectAsState().value) {
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