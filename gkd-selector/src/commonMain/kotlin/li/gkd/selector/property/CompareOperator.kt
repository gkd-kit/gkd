package li.gkd.selector.property

import kotlin.js.JsExport

@JsExport
/** Comparison operators that can produce complete text fast-query candidate sets. */
public sealed interface FastQueryOperator {
    public val key: String
}

internal fun FastQueryOperator.compareFastQueryValue(candidate: Any?, value: String): Boolean =
    when (this) {
        CompareOperator.Equal -> CompareOperator.Equal.compareValue(candidate, value)
        CompareOperator.Start -> CompareOperator.Start.compareValue(candidate, value)
        CompareOperator.Include -> CompareOperator.Include.compareValue(candidate, value)
        CompareOperator.End -> CompareOperator.End.compareValue(candidate, value)
    }

@JsExport
public sealed class CompareOperator(
    public val key: String,
    internal val expectedOperandDescription: String,
) {
    internal abstract fun allowType(
        left: ValueExpression,
        right: ValueExpression,
    ): Boolean

    public sealed class ValueOperator(
        key: String,
        expectedOperandDescription: String,
    ) : CompareOperator(key, expectedOperandDescription) {
        internal abstract fun compareValue(
            left: Any?,
            right: Any?,
        ): Boolean
    }

    public sealed class RegexOperator(
        key: String,
        expectedOperandDescription: String,
        internal val expectedMatch: Boolean,
    ) : CompareOperator(key, expectedOperandDescription)

    public data object Equal : ValueOperator("=", "compatible operands"), FastQueryOperator {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = true

        override fun compareValue(left: Any?, right: Any?): Boolean =
            comparePrimitiveValue(left, right)
    }

    public data object NotEqual : ValueOperator("!=", "compatible operands") {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = true

        override fun compareValue(left: Any?, right: Any?): Boolean =
            !comparePrimitiveValue(left, right)
    }

    public data object Start : ValueOperator("^=", "string operands"), FastQueryOperator {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isStringOperand && right.isStringOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is CharSequence && right is CharSequence && left.startsWith(right)
    }

    public data object NotStart : ValueOperator("!^=", "string operands") {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isStringOperand && right.isStringOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is CharSequence && right is CharSequence && !left.startsWith(right)
    }

    public data object Include : ValueOperator("*=", "string operands"), FastQueryOperator {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isStringOperand && right.isStringOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is CharSequence && right is CharSequence && left.contains(right)
    }

    public data object NotInclude : ValueOperator("!*=", "string operands") {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isStringOperand && right.isStringOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is CharSequence && right is CharSequence && !left.contains(right)
    }

    public data object End : ValueOperator("$=", "string operands"), FastQueryOperator {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isStringOperand && right.isStringOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is CharSequence && right is CharSequence && left.endsWith(right)
    }

    public data object NotEnd : ValueOperator("!$=", "string operands") {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isStringOperand && right.isStringOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is CharSequence && right is CharSequence && !left.endsWith(right)
    }

    public data object Less : ValueOperator("<", "int operands") {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isIntOperand && right.isIntOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is Int && right is Int && left < right
    }

    public data object LessEqual : ValueOperator("<=", "int operands") {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isIntOperand && right.isIntOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is Int && right is Int && left <= right
    }

    public data object More : ValueOperator(">", "int operands") {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isIntOperand && right.isIntOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is Int && right is Int && left > right
    }

    public data object MoreEqual : ValueOperator(">=", "int operands") {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left.isIntOperand && right.isIntOperand

        override fun compareValue(left: Any?, right: Any?): Boolean =
            left is Int && right is Int && left >= right
    }

    public data object Matches : RegexOperator(
        "~=",
        "variable and regular expression string literal",
        true,
    ) {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left is ValueExpression.Variable && right is ValueExpression.StringLiteral
    }

    public data object NotMatches : RegexOperator(
        "!~=",
        "variable and regular expression string literal",
        false,
    ) {
        override fun allowType(
            left: ValueExpression,
            right: ValueExpression,
        ): Boolean = left is ValueExpression.Variable && right is ValueExpression.StringLiteral
    }

    public companion object {
        internal val parseOrder: List<CompareOperator> by lazy {
            listOf(
                Equal,
                NotEqual,
                Start,
                NotStart,
                Include,
                NotInclude,
                End,
                NotEnd,
                Less,
                LessEqual,
                More,
                MoreEqual,
                Matches,
                NotMatches,
            ).sortedByDescending { it.key.length }
        }
    }
}

internal fun comparePrimitiveValue(left: Any?, right: Any?): Boolean {
    if (left !is CharSequence || right !is CharSequence) return left == right
    if (left === right) return true
    if (left.length != right.length) return false
    for (index in left.indices.reversed()) {
        if (left[index] != right[index]) return false
    }
    return true
}
