package li.gkd.selector.property

import li.gkd.selector.SelectorTypeKind

internal sealed class ValueExpression {
    abstract val syntaxTypeName: String

    open val isStringOperand: Boolean
        get() = false

    open val isIntOperand: Boolean
        get() = false

    sealed class Variable : ValueExpression() {
        override val syntaxTypeName: String
            get() = "variable"

        override val isStringOperand: Boolean
            get() = true

        override val isIntOperand: Boolean
            get() = true
    }

    enum class IdentifierRole {
        Other,
        Previous,
        Current,
        NullTolerantFunction,
    }

    class Identifier(
        val name: String,
    ) : Variable() {
        val role = when (name) {
            "prev" -> IdentifierRole.Previous
            "current" -> IdentifierRole.Current
            "equal", "notEqual" -> IdentifierRole.NullTolerantFunction
            else -> IdentifierRole.Other
        }
    }

    class MemberExpression(
        val object0: Variable,
        val property: String,
    ) : Variable()

    class CallExpression(
        val callee: Variable,
        val arguments: List<ValueExpression>,
    ) : Variable()

    sealed class LiteralExpression(
        open val value: Any?,
    ) : ValueExpression() {
        abstract val literalType: SelectorTypeKind?

        override val syntaxTypeName: String
            get() = literalType?.key ?: "null"
    }

    class NullLiteral : LiteralExpression(null) {
        override val literalType: SelectorTypeKind?
            get() = null
    }

    class BooleanLiteral(
        override val value: Boolean,
    ) : LiteralExpression(value) {
        override val literalType: SelectorTypeKind
            get() = SelectorTypeKind.BooleanType
    }

    class IntLiteral(
        override val value: Int,
    ) : LiteralExpression(value) {
        override val literalType: SelectorTypeKind
            get() = SelectorTypeKind.IntType

        override val isIntOperand: Boolean
            get() = true
    }

    class StringLiteral(
        override val value: String,
    ) : LiteralExpression(value) {
        override val literalType: SelectorTypeKind
            get() = SelectorTypeKind.StringType

        override val isStringOperand: Boolean
            get() = true
    }
}
