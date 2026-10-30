package li.gkd.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import li.gkd.app.ui.share.LocalIsTalkbackEnabled

@Composable
fun GkTooltipIconButtonBox(
    contentDescription: String?,
    showOnClick: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 视障用户使用 TalkBack 朗读 contentDescription，不需要 Tooltip
    if (contentDescription.isNullOrEmpty() || LocalIsTalkbackEnabled.current) {
        content()
    } else {
        val state = rememberTooltipState()
        val scope = rememberCoroutineScope()
        TooltipBox(
            tooltip = { PlainTooltip { Text(text = contentDescription) } },
            state = state,
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(
                    if (showOnClick) TooltipAnchorPosition.Above else TooltipAnchorPosition.Start,
                ),
        ) {
            if (showOnClick) {
                Box(Modifier.clickable { scope.launch { state.show() } }) { content() }
            } else {
                content()
            }
        }
    }
}
