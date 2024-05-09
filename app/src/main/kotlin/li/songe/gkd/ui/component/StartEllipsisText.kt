package li.songe.gkd.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit

/**
 * https://stackoverflow.com/a/77994659/10717907
 */
@Composable
fun StartEllipsisText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    softWrap: Boolean = true,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val layoutText = remember(text) { "$text $ellipsisText" }
    val textLayoutResultState = remember(layoutText) { mutableStateOf<TextLayoutResult?>(null) }

    SubcomposeLayout(modifier) { constraints ->
        subcompose("measure") {
            Text(
                text = layoutText,
                color = color,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign,
                lineHeight = lineHeight,
                softWrap = softWrap,
                maxLines = 1,
                onTextLayout = { textLayoutResultState.value = it },
                style = style,
            )
        }.first().measure(Constraints())

        val textLayoutResult =
            textLayoutResultState.value ?: return@SubcomposeLayout layout(0, 0) {}
        val placeable = subcompose("visible") {
            val finalText = remember(text, textLayoutResult, constraints.maxWidth) {
                if (
                    text.isEmpty() ||
                    textLayoutResult.getBoundingBox(text.indices.last).right <= constraints.maxWidth
                ) {
                    return@remember text
                }

                val ellipsisWidth = layoutText.indices.toList()
                    .takeLast(ellipsisCharactersCount)
                    .let widthLet@{ indices ->
                        for (i in indices) {
                            val width = textLayoutResult.getBoundingBox(i).width
                            if (width > 0) {
                                return@widthLet width * ellipsisCharactersCount
                            }
                        }
                        throw IllegalStateException("all ellipsis chars have invalid width")
                    }
                val availableWidth = constraints.maxWidth - ellipsisWidth
                val endCounter = BoundCounter(text, textLayoutResult) { text.indices.last - it }
                while (availableWidth - endCounter.width > 0) {
                    val possibleEndWidth = endCounter.widthWithNextChar()
                    if (availableWidth - possibleEndWidth >= 0) {
                        endCounter.addNextChar()
                    } else {
                        break
                    }
                }
                ellipsisText + endCounter.string.reversed().trimStart()
            }

            Text(
                text = finalText,
                color = color,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign,
                lineHeight = lineHeight,
                softWrap = softWrap,
                onTextLayout = onTextLayout,
                style = style,
            )
        }[0].measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

private const val ellipsisCharactersCount = 3
private const val ellipsisCharacter = '.'
private val ellipsisText =
    List(ellipsisCharactersCount) { ellipsisCharacter }.joinToString(separator = "")

private class BoundCounter(
    private val text: String,
    private val textLayoutResult: TextLayoutResult,
    private val charPosition: (Int) -> Int,
) {
    var string = ""
        private set
    var width = 0f
        private set

    private var _nextCharWidth: Float? = null
    private var invalidCharsCount = 0

    fun widthWithNextChar(): Float =
        width + nextCharWidth()

    private fun nextCharWidth(): Float =
        _nextCharWidth ?: run {
            var boundingBox: Rect
            invalidCharsCount--
            do {
                boundingBox = textLayoutResult
                    .getBoundingBox(charPosition(string.count() + ++invalidCharsCount))
            } while (boundingBox.right == 0f)
            _nextCharWidth = boundingBox.width
            boundingBox.width
        }

    fun addNextChar() {
        string += text[charPosition(string.count())]
        width += nextCharWidth()
        _nextCharWidth = null
    }
}