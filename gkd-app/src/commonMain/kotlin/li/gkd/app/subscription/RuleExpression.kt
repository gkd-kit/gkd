package li.gkd.app.subscription

import li.gkd.app.error.ErrorMessage
import li.gkd.app.resources.*
import net.objecthunter.exp4j.ExpressionBuilder

/** Screen and node coordinates are inputs, independent of Android's Rect and display singleton. */
data class RuleBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width() = right - left
    fun height() = bottom - top
}

sealed interface RuleValidationError {
    val message: ErrorMessage
        get() = when (this) {
            is SelectorError -> ErrorMessage.Resource(
                Res.string.selector_invalid_detail, listOf(ErrorMessage.Text(source), ErrorMessage.Text(detail)),
            )
            is InvalidPosition -> ErrorMessage.Resource(Res.string.rule_invalid_position, listOf(ErrorMessage.Text(value)))
        }

    data class SelectorError(val source: String, val detail: String) : RuleValidationError
    data class InvalidPosition(val value: String) : RuleValidationError
}

interface RuleExpression {
    fun setVariable(name: String, value: Double)
    fun evaluate(): Double
}

fun compileRuleExpression(source: String, variables: Set<String>): RuleExpression? = try {
    val expression = ExpressionBuilder(source).variables(variables).build()
    variables.forEach { expression.setVariable(it, 0.0) }
    if (expression.validate().isValid) object : RuleExpression {
        override fun setVariable(name: String, value: Double) {
            expression.setVariable(name, value)
        }

        override fun evaluate(): Double = expression.evaluate()
    } else null
} catch (_: Exception) {
    null
}
