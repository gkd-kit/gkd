package li.songe.gkd.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

private const val LAZY_TEXT_TARGET_CHUNK_SIZE = 2_000

@Composable
fun LazyCopyableText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    textToCopy: String = text.text,
    contentPadding: PaddingValues = PaddingValues.Zero,
    textStyle: TextStyle = LocalTextStyle.current,
    contentColor: Color = LocalContentColor.current,
    textContentDescription: String? = null,
) {
    val chunkRanges = remember(text.text) { buildTextChunkRanges(text.text) }
    CopyIconOverlay(
        textToCopy = textToCopy,
        copyIconVisible = true,
        contentColor = contentColor,
        modifier = modifier.defaultMinSize(
            minHeight = LocalMinimumInteractiveComponentSize.current,
        ),
    ) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .run {
                        if (textContentDescription == null) {
                            this
                        } else {
                            clearAndSetSemantics {
                                contentDescription = textContentDescription
                            }
                        }
                    },
                contentPadding = contentPadding,
            ) {
                items(
                    items = chunkRanges,
                    key = { it.first },
                ) { range ->
                    val chunk = remember(text, range) {
                        text.subSequence(range.first, range.last + 1)
                    }
                    BasicText(
                        text = chunk,
                        modifier = Modifier.fillMaxWidth(),
                        style = textStyle.copy(color = contentColor),
                    )
                }
            }
        }
    }
}

private fun buildTextChunkRanges(text: String): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var chunkStart = 0
    text.forEachIndexed { index, char ->
        if (
            char == '\n' &&
            index < text.lastIndex &&
            index + 1 - chunkStart >= LAZY_TEXT_TARGET_CHUNK_SIZE
        ) {
            // The item boundary represents this newline. Keeping it in the preceding BasicText
            // would add an extra empty visual line before the next item.
            ranges.add(chunkStart until index)
            chunkStart = index + 1
        }
    }
    if (chunkStart < text.length) {
        ranges.add(chunkStart until text.length)
    }
    return ranges
}
