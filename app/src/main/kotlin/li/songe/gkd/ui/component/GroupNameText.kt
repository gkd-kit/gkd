package li.songe.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import li.songe.gkd.ui.icon.SportsBasketball
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Composable
fun GroupNameText(
    modifier: Modifier = Modifier,
    preText: String? = null,
    isGlobal: Boolean,
    text: String,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    clickDisabled: Boolean = false,
) {
    if (isGlobal) {
        val text = remember(preText, text) {
            buildAnnotatedString {
                if (preText != null) {
                    append(preText)
                }
                appendInlineContent("icon")
                append(text)
            }
        }
        val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
        val inlineContent = remember(style, clickDisabled, textColor) {
            mapOf(
                "icon" to InlineTextContent(
                    placeholder = Placeholder(
                        width = style.fontSize,
                        height = style.lineHeight,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Icon(
                        imageVector = SportsBasketball,
                        modifier = Modifier
                            .runIf(!clickDisabled) {
                                clickable(onClick = throttle { toast("当前是全局规则组") })
                            }
                            .fillMaxSize(),
                        contentDescription = null,
                        tint = textColor
                    )
                }
            )
        }
        Text(
            modifier = modifier,
            text = text,
            inlineContent = inlineContent,
            style = style,
            color = color,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
        )
    } else {
        Text(
            modifier = modifier,
            text = if (preText.isNullOrEmpty()) {
                text
            } else {
                preText + text
            },
            style = style,
            color = color,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
        )
    }
}
