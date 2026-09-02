package li.gkd.selector

import kotlin.js.JsExport

@JsExport
public sealed class SelectorException(
    override val message: String,
) : Exception(message)

@JsExport
public class SelectorSyntaxException internal constructor(
    public val expected: String,
    public val actual: String,
    public val range: SourceRange,
    public val detail: String? = null,
) : SelectorException(
    buildSyntaxErrorMessage(expected, actual, range, detail),
) {
    public val index: Int
        get() = range.start
}

private fun buildSyntaxErrorMessage(
    expected: String,
    actual: String,
    range: SourceRange,
    detail: String?,
): String = buildString {
    append("Expected $expected, got $actual at index ${range.start}")
    detail?.let {
        append(": ")
        append(it)
    }
}

@JsExport
public enum class SelectorTypeErrorKind(
    internal val messagePrefix: String,
) {
    UnknownIdentifier("Unknown identifier"),
    UnknownMember("Unknown member"),
    UnknownMethod("Unknown method"),
    ArgumentCountMismatch("Argument count mismatch"),
    ArgumentTypeMismatch("Argument type mismatch"),
    OperandTypeMismatch("Operand type mismatch"),
    OperatorTypeMismatch("Operator type mismatch"),
}

@JsExport
public class SelectorTypeException internal constructor(
    public val kind: SelectorTypeErrorKind,
    public val expression: String,
    public val expected: String?,
    public val actual: String?,
    public val range: SourceRange?,
) : SelectorException(
    buildTypeErrorMessage(kind, expression, expected, actual),
) {
    public val index: Int?
        get() = range?.start
}

internal data class TypeCheckFailure(
    val kind: SelectorTypeErrorKind,
    val positionValue: Any,
    val expression: String,
    val expected: String? = null,
    val actual: String? = null,
) {
    fun toException(range: SourceRange?): SelectorTypeException = SelectorTypeException(
        kind = kind,
        expression = expression,
        expected = expected,
        actual = actual,
        range = range,
    )
}

private fun buildTypeErrorMessage(
    kind: SelectorTypeErrorKind,
    expression: String,
    expected: String?,
    actual: String?,
): String = buildString {
    append(kind.messagePrefix)
    append(": ")
    append(expression)
    expected?.let {
        append(", expected ")
        append(it)
    }
    actual?.let {
        append(", got ")
        append(it)
    }
}
