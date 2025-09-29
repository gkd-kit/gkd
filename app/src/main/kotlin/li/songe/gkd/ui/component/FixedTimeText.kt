package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

val LocalNumberCharWidth = compositionLocalOf<Dp> { error("not found DestinationsNavigator") }

@Composable
fun measureNumberTextWidth(style: TextStyle = LocalTextStyle.current): Dp {
    val textMeasurer = rememberTextMeasurer()
    val widthInPixels = "1234567890".map { c ->
        textMeasurer.measure(c.toString(), style).size.width
    }.average().toFloat()
    return with(LocalDensity.current) { widthInPixels.toDp() }
}

@Composable
fun FixedTimeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    charWidth: Dp = LocalNumberCharWidth.current,
) {
    Row(modifier = modifier) {
        text.forEach { c ->
            Text(
                text = c.toString(),
                style = style,
                modifier = if (c.isDigit()) {
                    Modifier.width(charWidth)
                } else {
                    Modifier
                },
                color = color,
                softWrap = false,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
