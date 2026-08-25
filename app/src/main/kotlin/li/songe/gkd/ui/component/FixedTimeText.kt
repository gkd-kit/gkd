package li.songe.gkd.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import li.songe.gkd.ui.style.TABULAR_NUMBERS_FONT_FEATURE

@Composable
fun FixedTimeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(fontFeatureSettings = TABULAR_NUMBERS_FONT_FEATURE),
        softWrap = false,
        maxLines = 1,
    )
}
