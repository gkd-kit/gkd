package li.gkd.selector.syntax

import li.gkd.selector.SelectorPositionKind
import li.gkd.selector.SourceMapped

internal class ParserContext(
    source: String,
    private val positionRecorder: PositionRecorder?,
) {
    val cursor = ParserCursor(source)

    inline fun <T : SourceMapped> positioned(
        kind: SelectorPositionKind,
        block: () -> T,
    ): T {
        val start = cursor.index
        return block().also { value ->
            record(value, kind, start)
        }
    }

    fun record(
        value: SourceMapped,
        kind: SelectorPositionKind,
        start: Int,
        end: Int = cursor.index,
    ) {
        positionRecorder?.record(value, kind, start, end)
    }

    fun recordPosition(
        kind: SelectorPositionKind,
        start: Int,
        end: Int = cursor.index,
    ) {
        positionRecorder?.recordPosition(kind, start, end)
    }
}
