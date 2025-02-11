package li.songe.selector.property

import li.songe.selector.FastQuery
import li.songe.selector.QueryContext
import li.songe.selector.Stringify
import li.songe.selector.Transform
import li.songe.selector.connect.CompareOperator
import kotlin.js.JsExport

@JsExport
data class PropertySegment(
    val at: Boolean,
    val name: String,
    val expressions: List<Expression>,
) : Stringify {
    private val matchAnyName = name.isEmpty() || name == "*"

    fun getBinaryExpressionList() = expressions.flatMap {
        it.getBinaryExpressionList().toList()
    }.toTypedArray()

    override fun stringify(): String {
        val matchTag = if (at) "@" else ""
        return matchTag + name + expressions.joinToString("") { "[${it.stringify()}]" }
    }

    private fun <T> matchName(node: T, transform: Transform<T>): Boolean {
        if (matchAnyName) return true
        val str = transform.getName(node) ?: return false
        if (str.length == name.length) {
            return str.contentEquals(name)
        } else if (str.length > name.length) {
            return str[str.length - name.length - 1] == '.' && str.endsWith(name)
        }
        return false
    }

    fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
    ): Boolean {
        return matchName(context.current, transform) && expressions.all { ex ->
            ex.match(
                context,
                transform
            )
        }
    }

    val fastQueryList: List<FastQuery>?
        get() {
            val exp = expressions.firstOrNull() ?: return null
            if (exp is LogicalExpression) {
                if (exp.operator != LogicalOperator.OrOperator) return null
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
                return List(1) { fq }
            }
            return null
        }

}

private fun expToFastQuery(e: BinaryExpression): FastQuery? {
    if (e.left !is ValueExpression.Identifier) return null
    if (e.right !is ValueExpression.StringLiteral) return null
    if (e.right.value.isEmpty()) return null
    if (e.left.value == "id" && e.operator == CompareOperator.Equal) {
        return FastQuery.Id(e.right.value)
    } else if (e.left.value == "vid" && e.operator == CompareOperator.Equal) {
        return FastQuery.Vid(e.right.value)
    } else if (e.left.value == "text" && (e.operator == CompareOperator.Equal || e.operator == CompareOperator.Start || e.operator == CompareOperator.Include || e.operator == CompareOperator.End)) {
        return FastQuery.Text(e.right.value)
    }
    return null
}
