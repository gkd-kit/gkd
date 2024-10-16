package li.songe.selector

import li.songe.selector.parser.selectorParser
import kotlin.js.JsExport

@JsExport
class Selector(
    val source: String,
    val propertyWrapper: PropertyWrapper
) : Stringify {
    override fun stringify(): String {
        return propertyWrapper.stringify()
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

    fun <T> matchContext(
        node: T,
        transform: Transform<T>,
        option: MatchOption,
    ): Context<T>? {
        return propertyWrapper.matchContext(Context(node), transform, option)
    }

    fun <T> match(
        node: T,
        transform: Transform<T>,
        option: MatchOption,
    ): T? {
        val ctx = matchContext(node, transform, option) ?: return null
        return ctx.get(targetIndex).current
    }

    val fastQueryList = propertyWrapper.fastQueryList
    val quickFindValue = if (fastQueryList.size == 1) fastQueryList.first() else null

    val isMatchRoot = propertyWrapper.isMatchRoot

    val connectWrappers = run {
        var c = propertyWrapper.to
        val keys = mutableListOf<ConnectWrapper>()
        while (c != null) {
            keys.add(c)
            c = c.to.to
        }
        keys.toTypedArray()
    }

    val binaryExpressions = run {
        var p: PropertyWrapper? = propertyWrapper
        val expressions = mutableListOf<BinaryExpression>()
        while (p != null) {
            val s = p.segment
            expressions.addAll(s.binaryExpressions)
            p = p.to?.to
        }
        expressions.distinct().toTypedArray()
    }

    fun isSlow(matchOption: MatchOption): Boolean {
        if (matchOption.quickFind && quickFindValue == null && !isMatchRoot) {
            return true
        }
        if ((!matchOption.fastQuery || propertyWrapper.fastQueryList.isEmpty()) && !isMatchRoot) {
            return true
        }
        if (connectWrappers.any { c -> c.segment.operator == ConnectOperator.Descendant && !(c.canFq && matchOption.fastQuery) }) {
            return true
        }
        return false
    }

    fun checkType(typeInfo: TypeInfo): SelectorCheckException? {
        try {
            binaryExpressions.forEach { exp ->
                if (!exp.operator.value.allowType(exp.left, exp.right)) {
                    throw MismatchOperatorTypeException(exp)
                }
                val leftType = getExpType(exp.left, typeInfo)
                val rightType = getExpType(exp.right, typeInfo)
                if (leftType != null && rightType != null && leftType != rightType) {
                    throw MismatchExpressionTypeException(exp, leftType, rightType)
                }
            }
        } catch (e: SelectorCheckException) {
            return e
        }
        return null
    }

    companion object {
        fun parse(source: String) = selectorParser(source)
        fun parseOrNull(source: String) = try {
            selectorParser(source)
        } catch (_: Exception) {
            null
        }
    }
}

private fun getExpType(exp: ValueExpression, typeInfo: TypeInfo): PrimitiveType? {
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
                } catch (e: SelectorCheckException) {
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

