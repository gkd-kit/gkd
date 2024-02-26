package li.songe.selector

import li.songe.selector.data.BinaryExpression
import li.songe.selector.data.CompareOperator
import li.songe.selector.data.ConnectOperator
import li.songe.selector.data.PrimitiveValue
import li.songe.selector.data.PropertyWrapper
import li.songe.selector.parser.ParserSet

class Selector internal constructor(private val propertyWrapper: PropertyWrapper) {
    override fun toString(): String {
        return propertyWrapper.toString()
    }

    val tracks = run {
        val list = mutableListOf(propertyWrapper)
        while (true) {
            list.add(list.last().to?.to ?: break)
        }
        list.map { p -> p.propertySegment.tracked }.toTypedArray<Boolean>()
    }

    val trackIndex = tracks.indexOfFirst { it }.let { i ->
        if (i < 0) 0 else i
    }

    fun <T> match(
        node: T,
        transform: Transform<T>,
        trackNodes: MutableList<T> = ArrayList(tracks.size),
    ): T? {
        val trackTempNodes = matchTracks(node, transform, trackNodes) ?: return null
        return trackTempNodes[trackIndex]
    }

    fun <T> matchTracks(
        node: T,
        transform: Transform<T>,
        trackNodes: MutableList<T> = ArrayList(tracks.size),
    ): List<T>? {
        return propertyWrapper.matchTracks(node, transform, trackNodes)
    }

    val qfIdValue = propertyWrapper.propertySegment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.name == "id" && e.operator == CompareOperator.Equal && e.value is PrimitiveValue.StringValue) {
            e.value.value
        } else {
            null
        }
    }

    val qfVidValue = propertyWrapper.propertySegment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.name == "vid" && e.operator == CompareOperator.Equal && e.value is PrimitiveValue.StringValue) {
            e.value.value
        } else {
            null
        }
    }

    val qfTextValue = propertyWrapper.propertySegment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.name == "text" && (e.operator == CompareOperator.Equal || e.operator == CompareOperator.Start || e.operator == CompareOperator.Include || e.operator == CompareOperator.End) && e.value is PrimitiveValue.StringValue) {
            e.value.value
        } else {
            null
        }
    }

    val canQf = qfIdValue != null || qfVidValue != null || qfTextValue != null

    // 主动查询
    val isMatchRoot = propertyWrapper.propertySegment.expressions.firstOrNull().let { e ->
        e is BinaryExpression && e.name == "depth" && e.operator == CompareOperator.Equal && e.value.value == 0
    }

    val connectKeys = run {
        var c = propertyWrapper.to
        val keys = mutableListOf<String>()
        while (c != null) {
            c.apply {
                keys.add(connectSegment.operator.key)
            }
            c = c.to.to
        }
        keys.toTypedArray()
    }

    private val binaryExpressions = run {
        var p: PropertyWrapper? = propertyWrapper
        val names = mutableListOf<BinaryExpression>()
        while (p != null) {
            val s = p.propertySegment
            names.addAll(s.binaryExpressions)
            p = p.to?.to
        }
        names.distinct().toTypedArray()
    }

    val propertyNames = run {
        binaryExpressions.map { e -> e.name }.distinct().toTypedArray()
    }


    // [name,type][]
    val nameToTypeList = run {
        binaryExpressions.map { e -> arrayOf(e.name, e.value.type) }.distinct().toTypedArray()
    }

    val canCacheIndex =
        connectKeys.contains(ConnectOperator.BeforeBrother.key) || connectKeys.contains(
            ConnectOperator.AfterBrother.key
        ) || propertyNames.contains("index")

    companion object {
        fun parse(source: String) = ParserSet.selectorParser(source)
        fun parseOrNull(source: String) = try {
            ParserSet.selectorParser(source)
        } catch (e: Exception) {
            null
        }
    }
}