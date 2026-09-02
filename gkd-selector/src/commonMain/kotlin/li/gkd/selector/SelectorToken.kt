package li.gkd.selector

import kotlin.js.JsExport

@JsExport
public enum class SelectorTokenKind {
    Whitespace,
    Selector,
    Identifier,
    Keyword,
    Integer,
    String,
    Invalid,
    CompareOperator,
    LogicalOperator,
    RelationOperator,
    ArithmeticOperator,
    PolynomialVariable,
    Punctuation,
    Target,
    Wildcard,
}

@JsExport
public enum class SelectorTokenScope {
    Selector,
    Property,
    Relation,
}

@JsExport
/**
 * A tolerant lexical token covering `start` (inclusive) to `end` (exclusive).
 * [scope] provides the coarse syntax context needed by context-sensitive highlighters.
 */
public data class SelectorToken(
    val kind: SelectorTokenKind,
    val scope: SelectorTokenScope,
    val start: Int,
    val end: Int,
)
