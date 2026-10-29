package li.gkd.app.subscription

import li.gkd.app.model.ScreenSize
import li.songe.expression.ExpressionEvaluationException
import li.songe.expression.ExpressionParseException

/** Compiles every supplied field once, then applies anchor precedence only to valid input. */
class RulePositionCompilation(position: RawSubscription.Position) {
    private val fields = linkedMapOf<String, RuleExpression>()
    private val horizontal: String?
    private val vertical: String?
    val error: PositionError?

    init {
        var failure: PositionError? = null
        for ((name, source) in
            listOf(
                "left" to position.left,
                "top" to position.top,
                "right" to position.right,
                "bottom" to position.bottom,
                "x" to position.x,
                "y" to position.y,
            )) {
            if (source == null) continue
            val compiled =
                compileRuleExpression(source) { cause ->
                    if (failure == null)
                        failure =
                            PositionError(
                                name,
                                when (cause) {
                                    is ExpressionParseException -> cause.code.name
                                    is ExpressionEvaluationException -> cause.code.name
                                },
                                source,
                                cause.startIndex,
                                cause.endIndex,
                                cause.message,
                            )
                }
            if (compiled != null) fields[name] = compiled
        }
        horizontal = listOf("left", "right", "x").firstOrNull { it in fields }
        vertical = listOf("top", "bottom", "y").firstOrNull { it in fields }
        error =
            failure
                ?: when {
                    horizontal == null -> PositionError("x", "MISSING_HORIZONTAL_COORDINATE")
                    vertical == null -> PositionError("y", "MISSING_VERTICAL_COORDINATE")
                    else -> null
                }
    }

    fun calculate(
        bounds: RuleBounds,
        screenSize: ScreenSize,
        fieldPrefix: String,
        captureInputs: Boolean,
    ): Pair<Float, Float> {
        error?.let { throw PositionCalculationException(it.at(fieldPrefix)) }
        fun coordinate(name: String): Float {
            val expression = fields.getValue(name)
            var inputs: Map<String, Double>? = null
            val value =
                try {
                    expression
                        .evaluate(
                            bounds,
                            screenSize,
                            if (captureInputs) { values -> inputs = values } else null,
                        )
                        .toFloat()
                } catch (cause: ExpressionEvaluationException) {
                    throw PositionCalculationException(
                        PositionError(
                            "$fieldPrefix.$name",
                            cause.code.name,
                            expression.source,
                            cause.startIndex,
                            cause.endIndex,
                            cause.message,
                            inputs,
                        ),
                        cause,
                    )
                }
            val coordinate =
                when (name) {
                    "left" -> bounds.left + value
                    "right" -> bounds.right - value
                    "top" -> bounds.top + value
                    "bottom" -> bounds.bottom - value
                    else -> value
                }
            if (!coordinate.isFinite())
                throw PositionCalculationException(
                    PositionError(
                        "$fieldPrefix.$name",
                        "NON_FINITE_COORDINATE",
                        expression.source,
                        0,
                        expression.source.length,
                        inputs = inputs,
                    ),
                )
            return coordinate
        }
        return coordinate(checkNotNull(horizontal)) to coordinate(checkNotNull(vertical))
    }
}
