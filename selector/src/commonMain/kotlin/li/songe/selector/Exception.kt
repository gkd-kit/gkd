package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed class SelectorCheckException(override val message: String) : Exception(message)

@JsExport
data class UnknownIdentifierException(
    val value: ValueExpression.Identifier,
) : SelectorCheckException("Unknown Identifier: ${value.value}")

@JsExport
data class UnknownMemberException(
    val value: ValueExpression.MemberExpression,
) : SelectorCheckException("Unknown Member: ${value.property}")

@JsExport
data class UnknownIdentifierMethodException(
    val value: ValueExpression.Identifier,
) : SelectorCheckException("Unknown Identifier Method: ${value.value}")

@JsExport
data class UnknownMemberMethodException(
    val value: ValueExpression.MemberExpression,
) : SelectorCheckException("Unknown Member Method: ${value.property}")

@JsExport
data class MismatchParamTypeException(
    val call: ValueExpression.CallExpression,
    val argument: ValueExpression.LiteralExpression,
    val type: PrimitiveType
) : SelectorCheckException("Mismatch Param Type: ${argument.value} should be ${type.key}")

@JsExport
data class MismatchExpressionTypeException(
    val exception: BinaryExpression,
    val leftType: PrimitiveType,
    val rightType: PrimitiveType,
) : SelectorCheckException("Mismatch Expression Type: ${exception.stringify()}")

@JsExport
data class MismatchOperatorTypeException(
    val exception: BinaryExpression,
) : SelectorCheckException("Mismatch Operator Type: ${exception.stringify()}")
