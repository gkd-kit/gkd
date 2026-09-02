package li.gkd.selector

import kotlin.js.JsExport

@JsExport
public enum class SelectorPositionKind {
    Selector,
    Unit,
    LogicalSelector,
    NegatedSelector,
    PropertySelector,
    Property,
    Comparison,
    LogicalCondition,
    NegatedCondition,
    NullLiteral,
    BooleanLiteral,
    IntLiteral,
    StringLiteral,
    Identifier,
    MemberAccess,
    Call,
    Relation,
    TupleRange,
    PolynomialRange,
}

@JsExport
/** A source range from `start` (inclusive) to `end` (exclusive). */
public data class SourceRange(
    val start: Int,
    val end: Int,
)

@JsExport
/** The source range and semantic kind of a successfully parsed value. */
public data class SelectorPosition(
    val kind: SelectorPositionKind,
    val start: Int,
    val end: Int,
)

internal class SelectorSourceMap(
    private val ranges: Map<Any, SourceRange>,
) {
    fun rangeOf(value: Any): SourceRange? = ranges[value]
}
