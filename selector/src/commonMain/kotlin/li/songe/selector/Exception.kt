package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed class SelectorCheckException(override val message: String) : Exception(message)

@JsExport
data class UnknownIdentifierException(
    val value: ValueExpression.Identifier,
) : SelectorCheckException("Unknown Identifier: ${value.stringify()}")

@JsExport
data class UnknownMemberException(
    val value: ValueExpression.MemberExpression,
) : SelectorCheckException("Unknown Member: ${value.stringify()}")

@JsExport
data class UnknownIdentifierMethodException(
    val value: ValueExpression.Identifier,
) : SelectorCheckException("Unknown Identifier Method: ${value.stringify()}")

@JsExport
data class UnknownIdentifierMethodParamsException(
    val value: ValueExpression.CallExpression,
) : SelectorCheckException("Unknown Identifier Method Params: ${value.stringify()}")

@JsExport
data class UnknownMemberMethodException(
    val value: ValueExpression.MemberExpression,
) : SelectorCheckException("Unknown Member Method: ${value.stringify()}")

@JsExport
data class UnknownMemberMethodParamsException(
    val value: ValueExpression.CallExpression,
) : SelectorCheckException("Unknown Member Method Params: ${value.stringify()}")

@JsExport
data class MismatchParamTypeException(
    val call: ValueExpression.CallExpression,
    val argument: ValueExpression,
    val type: PrimitiveType
) : SelectorCheckException("Mismatch Param Type: ${argument.stringify()} should be ${type.key}")

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
