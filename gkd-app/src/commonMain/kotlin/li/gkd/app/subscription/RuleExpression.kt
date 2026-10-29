package li.gkd.app.subscription

import kotlin.random.Random
import li.gkd.app.error.ErrorMessage
import li.gkd.app.model.ScreenSize
import li.gkd.app.resources.*
import li.songe.expression.CompiledExpression
import li.songe.expression.Expression
import li.songe.expression.ExpressionException
import li.songe.expression.ExpressionParseException

/** Screen and node coordinates are inputs, independent of Android's Rect and display singleton. */
data class RuleBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width() = right - left

    fun height() = bottom - top
}

sealed interface RuleValidationError {
    val message: ErrorMessage
        get() =
            when (this) {
                is SelectorError ->
                    ErrorMessage.Resource(
                        Res.string.selector_invalid_detail,
                        listOf(ErrorMessage.Text(source), ErrorMessage.Text(detail)),
                    )
                is InvalidPosition -> error.message
                is InvalidVersionPattern ->
                    ErrorMessage.Resource(
                        Res.string.rule_invalid_version_pattern,
                        listOf(
                            ErrorMessage.Text(this.field),
                            ErrorMessage.Text(source),
                            ErrorMessage.Text(detail),
                        ),
                    )
            }

    data class SelectorError(val source: String, val detail: String) : RuleValidationError

    data class InvalidPosition(val error: PositionError) : RuleValidationError

    data class InvalidVersionPattern(val field: String, val source: String, val detail: String) :
        RuleValidationError
}

class RuleExpression(private val compiled: CompiledExpression) {
    val source: String
        get() = compiled.source

    fun evaluate(
        bounds: RuleBounds,
        screenSize: ScreenSize,
        captureInputs: ((Map<String, Double>) -> Unit)? = null,
    ): Double {
        val inputs =
            buildMap(compiled.variables.size) {
                for (name in compiled.variables) put(
                    name,
                    when (name) {
                        "left" -> bounds.left.toDouble()
                        "top" -> bounds.top.toDouble()
                        "right" -> bounds.right.toDouble()
                        "bottom" -> bounds.bottom.toDouble()
                        "width" -> bounds.width().toDouble()
                        "height" -> bounds.height().toDouble()
                        "screenWidth" -> screenSize.width.toDouble()
                        "screenHeight" -> screenSize.height.toDouble()
                        "random" -> Random.nextDouble()
                        else -> error("Unknown coordinate variable: $name")
                    },
                )
            }
        captureInputs?.invoke(inputs)
        return compiled.evaluate(inputs)
    }
}

private val positionVariables =
    setOf(
        "left",
        "top",
        "right",
        "bottom",
        "width",
        "height",
        "random",
        "screenWidth",
        "screenHeight",
    )

fun compileRuleExpression(
    source: String,
    onError: (ExpressionException) -> Unit = {},
): RuleExpression? {
    val compiled =
        try {
            Expression.compile(source)
        } catch (error: ExpressionParseException) {
            onError(error)
            return null
        }
    compiled.validateBindings(positionVariables).firstOrNull()?.let {
        onError(it)
        return null
    }
    return RuleExpression(compiled)
}
