package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class QuickFindValue(
    val id: String?,
    val vid: String?,
    val text: String?,
) {
    val canQf = id != null || vid != null || text != null
}

internal fun getQuickFindValue(segment: PropertySegment): QuickFindValue {
    val id = segment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.left.value == "id" && e.operator.value == CompareOperator.Equal && e.right is ValueExpression.StringLiteral) {
            e.right.value
        } else {
            null
        }
    }
    val vid = segment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.left.value == "vid" && e.operator.value == CompareOperator.Equal && e.right is ValueExpression.StringLiteral) {
            e.right.value
        } else {
            null
        }
    }
    val text = segment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.left.value == "text" && (e.operator.value == CompareOperator.Equal || e.operator.value == CompareOperator.Start || e.operator.value == CompareOperator.Include || e.operator.value == CompareOperator.End) && e.right is ValueExpression.StringLiteral) {
            e.right.value
        } else {
            null
        }
    }
    return QuickFindValue(id, vid, text)
}