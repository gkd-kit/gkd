package li.songe.selector

import li.songe.selector.data.BinaryExpression
import li.songe.selector.data.CompareOperator
import li.songe.selector.data.PropertyWrapper
import li.songe.selector.parser.ParserSet


class Selector internal constructor(private val propertyWrapper: PropertyWrapper) {
    override fun toString(): String {
        return propertyWrapper.toString()
    }

    val tracks by lazy {
        val list = mutableListOf(propertyWrapper)
        while (true) {
            list.add(list.last().to?.to ?: break)
        }
        list.map { p -> p.propertySegment.tracked }.toTypedArray()
    }

    val trackIndex = tracks.indexOfFirst { it }.let { i ->
        if (i < 0) 0 else i
    }

    fun <T> match(
        node: T,
        transform: Transform<T>,
        trackNodes: MutableList<T> = mutableListOf(),
    ): T? {
        val trackTempNodes = matchTracks(node, transform, trackNodes) ?: return null
        return trackTempNodes[trackIndex]
    }

    fun <T> matchTracks(
        node: T,
        transform: Transform<T>,
        trackNodes: MutableList<T> = mutableListOf(),
    ): List<T>? {
        return propertyWrapper.matchTracks(node, transform, trackNodes)
    }

    // true: use findById, false: use findByText
    val canQuickFind = propertyWrapper.propertySegment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.value is String) {
            if (e.name == "id" && e.operator == CompareOperator.Equal) {
                true to e.value
            } else if (e.name == "text" && (e.operator == CompareOperator.Equal || e.operator == CompareOperator.Start || e.operator == CompareOperator.Include || e.operator == CompareOperator.End)) {
                false to e.value
            } else {
                null
            }
        } else {
            null
        }
    }

    // 主动查询
    val isMatchRoot = propertyWrapper.propertySegment.expressions.firstOrNull().let { e ->
        e is BinaryExpression && e.name == "depth" && e.operator == CompareOperator.Equal && e.value == 0
    }

    companion object {
        fun parse(source: String) = ParserSet.selectorParser(source)
        fun check(source: String): Boolean {
            return try {
                ParserSet.selectorParser(source)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}