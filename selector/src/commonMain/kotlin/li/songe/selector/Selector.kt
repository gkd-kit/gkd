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
    ): Context<T>? {
        return propertyWrapper.matchContext(Context(node), transform)
    }

    fun <T> match(
        node: T,
        transform: Transform<T>,
    ): T? {
        val ctx = matchContext(node, transform) ?: return null
        return ctx.get(targetIndex).current
    }

    val quickFindValue = propertyWrapper.quickFindValue

    val isMatchRoot = propertyWrapper.isMatchRoot

    val connectKeys = run {
        var c = propertyWrapper.to
        val keys = mutableListOf<String>()
        while (c != null) {
            c.apply {
                keys.add(segment.operator.key)
            }
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

    val useCache = run {
        if (connectKeys.isNotEmpty()) {
            return@run true
        }
        binaryExpressions.forEach { b ->
            if (b.properties.any { useCacheProperties.contains(it) }) {
                return@run true
            }
            if (b.methods.any { useCacheMethods.contains(it) }) {
                return@run true
            }
        }
        return@run false
    }

    fun checkType(typeInfo: TypeInfo): SelectorCheckException? {
        try {
            propertyWrapper.segment.binaryExpressions.forEach { exp ->
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
        } catch (e: Exception) {
            null
        }
    }
}

private val useCacheProperties by lazy {
    arrayOf("index", "parent", "depth")
}
private val useCacheMethods by lazy {
    arrayOf("getChild")
}

private fun getExpType(exp: ValueExpression, typeInfo: TypeInfo): PrimitiveType? {
    return when (exp) {
        is ValueExpression.NullLiteral -> null
        is ValueExpression.BooleanLiteral -> PrimitiveType.BooleanType
        is ValueExpression.IntLiteral -> PrimitiveType.IntType
        is ValueExpression.StringLiteral -> PrimitiveType.StringType
        is ValueExpression.Variable -> checkVariable(exp, typeInfo).type
    }
}

private fun checkVariable(value: ValueExpression.Variable, typeInfo: TypeInfo): TypeInfo {
    return when (value) {
        is ValueExpression.CallExpression -> {
            val method = when (value.callee) {
                is ValueExpression.CallExpression -> {
                    throw IllegalArgumentException("Unsupported nested call")
                }

                is ValueExpression.Identifier -> {
                    // getChild(0)
                    typeInfo.methods.find { it.name == value.callee.value && it.params.size == value.arguments.size }
                        ?: throw UnknownIdentifierMethodException(value.callee)
                }

                is ValueExpression.MemberExpression -> {
                    // parent.getChild(0)
                    checkVariable(
                        value.callee.object0, typeInfo
                    ).methods.find { it.name == value.callee.property && it.params.size == value.arguments.size }
                        ?: throw UnknownMemberMethodException(value.callee)
                }
            }
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
                        checkVariable(argExp, argTypeInfo)
                    }
                }
            }
            return method.returnType
        }

        is ValueExpression.Identifier -> {
            typeInfo.props.find { it.name == value.value }?.type
                ?: throw UnknownIdentifierException(value)
        }

        is ValueExpression.MemberExpression -> {
            checkVariable(value.object0, typeInfo).props.find { it.name == value.property }?.type
                ?: throw UnknownMemberException(value)
        }
    }
}

