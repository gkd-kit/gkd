package li.songe.selector

import li.songe.selector.property.BinaryExpression
import li.songe.selector.property.ValueExpression
import kotlin.js.JsExport

@JsExport
sealed class GkdException(override val message: String) : Exception(message) {
    // for kotlin js
    @Suppress("unused")
    val outMessage: String
        get() = message
}

@JsExport
data class SyntaxException(
    override val message: String,
    val expectedValue: String,
    val index: Int
) : GkdException(message)

@JsExport
sealed class TypeException(override val message: String) : GkdException(message)

@JsExport
data class UnknownIdentifierException(
    val value: ValueExpression.Identifier,
) : TypeException("Unknown Identifier: ${value.stringify()}")

@JsExport
data class UnknownMemberException(
    val value: ValueExpression.MemberExpression,
) : TypeException("Unknown Member: ${value.stringify()}")

@JsExport
data class UnknownIdentifierMethodException(
    val value: ValueExpression.Identifier,
) : TypeException("Unknown Identifier Method: ${value.stringify()}")

@JsExport
data class UnknownIdentifierMethodParamsException(
    val value: ValueExpression.CallExpression,
) : TypeException("Unknown Identifier Method Params: ${value.stringify()}")

@JsExport
data class UnknownMemberMethodException(
    val value: ValueExpression.MemberExpression,
) : TypeException("Unknown Member Method: ${value.stringify()}")

@JsExport
data class UnknownMemberMethodParamsException(
    val value: ValueExpression.CallExpression,
) : TypeException("Unknown Member Method Params: ${value.stringify()}")

@JsExport
data class MismatchParamTypeException(
    val call: ValueExpression.CallExpression,
    val argument: ValueExpression,
    val type: PrimitiveType
) : TypeException("Mismatch Param Type: ${argument.stringify()} should be ${type.key}")

@JsExport
data class MismatchExpressionTypeException(
    val exception: BinaryExpression,
    val leftType: PrimitiveType,
    val rightType: PrimitiveType,
) : TypeException("Mismatch Expression Type: ${exception.stringify()}")

@JsExport
data class MismatchOperatorTypeException(
    val exception: BinaryExpression,
) : TypeException("Mismatch Operator Type: ${exception.stringify()}")
