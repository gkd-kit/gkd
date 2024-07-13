package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed class FastQuery(open val value: String) : Stringify {
    override fun stringify() = value
    data class Id(override val value: String) : FastQuery(value)
    data class Vid(override val value: String) : FastQuery(value)
    data class Text(override val value: String) : FastQuery(value)
}

internal fun getFastQueryList(segment: PropertySegment): List<FastQuery>? {
    val exp = segment.expressions.firstOrNull() ?: return null
    if (exp is LogicalExpression) {
        if (exp.operator.value != LogicalOperator.OrOperator) return null
        val expArray = exp.getSameExpressionArray() ?: return null
        val list = mutableListOf<FastQuery>()
        expArray.forEach { e ->
            val fq = expToFastQuery(e) ?: return null
            list.add(fq)
        }
        return list
    }
    if (exp is BinaryExpression) {
        val fq = expToFastQuery(exp) ?: return null
        return listOf(fq)
    }
    return null
}

private fun expToFastQuery(e: BinaryExpression): FastQuery? {
    if (e.left !is ValueExpression.Identifier) return null
    if (e.right !is ValueExpression.StringLiteral) return null
    if (e.right.value.isEmpty()) return null
    if (e.left.value == "id" && e.operator.value == CompareOperator.Equal) {
        return FastQuery.Id(e.right.value)
    } else if (e.left.value == "vid" && e.operator.value == CompareOperator.Equal) {
        return FastQuery.Vid(e.right.value)
    } else if (e.left.value == "text" && (e.operator.value == CompareOperator.Equal || e.operator.value == CompareOperator.Start || e.operator.value == CompareOperator.Include || e.operator.value == CompareOperator.End)) {
        return FastQuery.Text(e.right.value)
    }
    return null
}