package li.gkd.selector.unit

import li.gkd.selector.MatchOption
import li.gkd.selector.MethodInfo
import li.gkd.selector.MismatchExpressionTypeException
import li.gkd.selector.MismatchOperatorTypeException
import li.gkd.selector.MismatchParamTypeException
import li.gkd.selector.PrimitiveType
import li.gkd.selector.QueryContext
import li.gkd.selector.QueryResult
import li.gkd.selector.Transform
import li.gkd.selector.TypeException
import li.gkd.selector.TypeInfo
import li.gkd.selector.UnknownIdentifierException
import li.gkd.selector.UnknownIdentifierMethodException
import li.gkd.selector.UnknownIdentifierMethodParamsException
import li.gkd.selector.UnknownMemberException
import li.gkd.selector.UnknownMemberMethodException
import li.gkd.selector.UnknownMemberMethodParamsException
import li.gkd.selector.connect.ConnectOperator
import li.gkd.selector.connect.ConnectSegment
import li.gkd.selector.connect.ConnectWrapper
import li.gkd.selector.property.BinaryExpression
import li.gkd.selector.property.PropertyWrapper
import li.gkd.selector.property.ValueExpression
import kotlin.collections.addAll
import kotlin.js.JsExport

@JsExport
data class UnitSelectorExpression(
    val propertyWrapper: PropertyWrapper,
) : SelectorExpression() {
    override fun stringify(): String {
        return propertyWrapper.stringify()
    }

    override fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption
    ): T? {
        propertyWrapper.matchContext(context, transform, option).apply {
            if (matched) {
                return get(targetIndex).current
            }
        }
        return null
    }

    override fun <T> matchContext(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption,
    ): QueryResult<T> {
        return QueryResult.UnitResult(
            propertyWrapper.matchContext(context, transform, option),
            this,
            targetIndex
        )
    }

    val targetIndex = run {
        val length = propertyWrapper.length
        var index = 0
        var c: PropertyWrapper? = propertyWrapper
        while (c != null) {
            if (c.segment.at) {
                return@run length - 1 - index
            }
            c = c.to?.to
            index++
        }
        length - 1
    }

    override val fastQueryList = propertyWrapper.fastQueryList
    override val isMatchRoot = propertyWrapper.isMatchRoot

    internal val connectWrappers: Sequence<ConnectWrapper>
        get() = sequence {
            var c = propertyWrapper.to
            while (c != null) {
                yield(c)
                c = c.to.to
            }
        }

    override val binaryExpressionList: List<BinaryExpression>
        get() {
            var p: PropertyWrapper? = propertyWrapper
            val expressions = mutableListOf<BinaryExpression>()
            while (p != null) {
                val s = p.segment
                expressions.addAll(s.getBinaryExpressionList())
                p = p.to?.to
            }
            return expressions
        }
    override val connectSegmentList: List<ConnectSegment>
        get() = connectWrappers.map { it.segment }.toList()


    override fun isSlow(matchOption: MatchOption): Boolean {
        if ((!matchOption.fastQuery || propertyWrapper.fastQueryList.isEmpty()) && !isMatchRoot) {
            return true
        }
        if (connectWrappers.any { c -> c.segment.operator == ConnectOperator.Descendant && !(c.canFq && matchOption.fastQuery) }) {
            return true
        }
        return false
    }

    @Throws(TypeException::class)
    override fun checkType(typeInfo: TypeInfo) {
        binaryExpressionList.forEach { exp ->
            if (!exp.operator.allowType(exp.left, exp.right)) {
                throw MismatchOperatorTypeException(exp)
            }
            val leftType = getExpType(exp.left, typeInfo)
            val rightType = getExpType(exp.right, typeInfo)
            if (leftType != null && rightType != null && leftType != rightType) {
                throw MismatchExpressionTypeException(exp, leftType, rightType)
            }
        }
    }

}

private fun getExpType(
    exp: ValueExpression,
    typeInfo: TypeInfo
): PrimitiveType? {
    return when (exp) {
        is ValueExpression.NullLiteral -> null
        is ValueExpression.BooleanLiteral -> PrimitiveType.BooleanType
        is ValueExpression.IntLiteral -> PrimitiveType.IntType
        is ValueExpression.StringLiteral -> PrimitiveType.StringType
        is ValueExpression.Variable -> checkVariable(exp, typeInfo, typeInfo).type
    }
}

private fun checkMethod(
    method: MethodInfo,
    value: ValueExpression.CallExpression,
    globalTypeInfo: TypeInfo
): TypeInfo {
    method.params.forEachIndexed { index, argTypeInfo ->
        when (val argExp = value.arguments[index]) {
            is ValueExpression.NullLiteral -> {}
            is ValueExpression.BooleanLiteral -> {
                if (argTypeInfo.type != PrimitiveType.BooleanType) {
                    throw MismatchParamTypeException(
                        value,
                        argExp,
                        PrimitiveType.BooleanType
                    )
                }
            }

            is ValueExpression.IntLiteral -> {
                if (argTypeInfo.type != PrimitiveType.IntType) {
                    throw MismatchParamTypeException(value, argExp, PrimitiveType.IntType)
                }
            }

            is ValueExpression.StringLiteral -> {
                if (argTypeInfo.type != PrimitiveType.StringType) {
                    throw MismatchParamTypeException(
                        value,
                        argExp,
                        PrimitiveType.StringType
                    )
                }
            }

            is ValueExpression.Variable -> {
                val type = checkVariable(argExp, argTypeInfo, globalTypeInfo)
                if (type.type != argTypeInfo.type) {
                    throw MismatchParamTypeException(
                        value,
                        argExp,
                        type.type
                    )
                }
            }
        }
    }
    return method.returnType
}

private fun checkVariable(
    value: ValueExpression.Variable,
    currentTypeInfo: TypeInfo,
    globalTypeInfo: TypeInfo,
): TypeInfo {
    return when (value) {
        is ValueExpression.CallExpression -> {
            val methods = when (value.callee) {
                is ValueExpression.CallExpression -> {
                    throw IllegalArgumentException("Unsupported nested call")
                }

                is ValueExpression.Identifier -> {
                    // getChild(0)
                    globalTypeInfo.methods
                        .filter { it.name == value.callee.value }
                        .apply {
                            if (isEmpty()) {
                                throw UnknownIdentifierMethodException(value.callee)
                            }
                        }
                        .filter { it.params.size == value.arguments.size }
                        .apply {
                            if (isEmpty()) {
                                throw UnknownIdentifierMethodParamsException(value)
                            }
                        }
                }

                is ValueExpression.MemberExpression -> {
                    // parent.getChild(0)
                    checkVariable(
                        value.callee.object0,
                        currentTypeInfo,
                        globalTypeInfo
                    ).methods
                        .filter { it.name == value.callee.property }
                        .apply {
                            if (isEmpty()) {
                                throw UnknownMemberMethodException(value.callee)
                            }
                        }.filter { it.params.size == value.arguments.size }.apply {
                            if (isEmpty()) {
                                throw UnknownMemberMethodParamsException(value)
                            }
                        }
                }
            }
            if (methods.size == 1) {
                checkMethod(methods[0], value, globalTypeInfo)
                return methods[0].returnType
            }
            methods.forEachIndexed { i, method ->
                try {
                    checkMethod(method, value, globalTypeInfo)
                    return method.returnType
                } catch (e: TypeException) {
                    if (i == methods.size - 1) {
                        throw e
                    }
                    // ignore
                }
            }
            if (value.callee is ValueExpression.Identifier) {
                throw UnknownIdentifierMethodException(value.callee)
            } else if (value.callee is ValueExpression.MemberExpression) {
                throw UnknownMemberMethodException(value.callee)
            }
            throw IllegalArgumentException("Unsupported nested call")
        }

        is ValueExpression.Identifier -> {
            globalTypeInfo.props.find { it.name == value.value }?.type
                ?: throw UnknownIdentifierException(value)
        }

        is ValueExpression.MemberExpression -> {
            checkVariable(
                value.object0,
                currentTypeInfo,
                globalTypeInfo
            ).props.find { it.name == value.property }?.type
                ?: throw UnknownMemberException(value)
        }
    }
}
