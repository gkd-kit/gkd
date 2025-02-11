package li.songe.selector.unit

import li.songe.selector.MatchOption
import li.songe.selector.MethodInfo
import li.songe.selector.MismatchExpressionTypeException
import li.songe.selector.MismatchOperatorTypeException
import li.songe.selector.MismatchParamTypeException
import li.songe.selector.PrimitiveType
import li.songe.selector.QueryContext
import li.songe.selector.QueryResult
import li.songe.selector.Transform
import li.songe.selector.TypeException
import li.songe.selector.TypeInfo
import li.songe.selector.UnknownIdentifierException
import li.songe.selector.UnknownIdentifierMethodException
import li.songe.selector.UnknownIdentifierMethodParamsException
import li.songe.selector.UnknownMemberException
import li.songe.selector.UnknownMemberMethodException
import li.songe.selector.UnknownMemberMethodParamsException
import li.songe.selector.connect.ConnectOperator
import li.songe.selector.connect.ConnectWrapper
import li.songe.selector.property.BinaryExpression
import li.songe.selector.property.PropertyWrapper
import li.songe.selector.property.ValueExpression
import kotlin.collections.addAll

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

    private val connectWrappers: Sequence<ConnectWrapper>
        get() = sequence {
            var c = propertyWrapper.to
            while (c != null) {
                yield(c)
                c = c.to.to
            }
        }

    private fun getBinaryExpressionList(): List<BinaryExpression> {
        var p: PropertyWrapper? = propertyWrapper
        val expressions = mutableListOf<BinaryExpression>()
        while (p != null) {
            val s = p.segment
            expressions.addAll(s.getBinaryExpressionList())
            p = p.to?.to
        }
        return expressions
    }

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
        getBinaryExpressionList().forEach { exp ->
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
