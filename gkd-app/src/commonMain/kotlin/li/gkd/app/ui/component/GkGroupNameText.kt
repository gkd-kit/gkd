package li.gkd.app.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import li.gkd.app.ui.icon.SportsBasketball

@Composable
fun GkGroupNameText(
    modifier: Modifier = Modifier,
    preText: String? = null,
    isGlobal: Boolean,
    text: String,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    categoryName: String? = null,
    hideCategoryPrefix: Boolean = false,
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
        val inlineContent = remember(style, textColor) {
            mapOf(
                "icon" to InlineTextContent(
                    placeholder = Placeholder(
                        width = style.fontSize,
                        height = style.lineHeight,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    GkIcon(
                        imageVector = SportsBasketball,
                        modifier = Modifier.fillMaxSize(),
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
        val categoryColor = MaterialTheme.colorScheme.primary
        val displayText = remember(preText, text, categoryName, categoryColor, hideCategoryPrefix) {
            buildAnnotatedString {
                append(preText.orEmpty())
                val category = categoryName?.takeIf { it.isNotBlank() && text.startsWith(it) }
                if (category == null) {
                    append(text)
                } else if (hideCategoryPrefix) {
                    append(text.removeRuleCategoryPrefix(category).ifEmpty { text })
                } else {
                    withStyle(SpanStyle(color = categoryColor, fontWeight = FontWeight.Medium)) {
                        append(category)
                    }
                    // Normalize only the separator after the matched prefix, never the stored name.
                    val remainder = text.removeRuleCategoryPrefix(category)
                    if (remainder.isNotEmpty()) {
                        append(' ')
                        append(remainder)
                    }
                }
            }
        }
        Text(
            modifier = modifier,
            text = displayText,
            style = style,
            color = color,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
        )
    }
}

// Strip only the matched prefix and its separator; an empty result is valid here.
fun String.removeRuleCategoryPrefix(categoryName: String): String =
    removePrefix(categoryName).trimStart().removePrefix("-").trimStart()
