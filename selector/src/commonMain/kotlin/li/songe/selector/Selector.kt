package li.songe.selector

import li.songe.selector.parser.selectorParser

class Selector internal constructor(
    val source: String, private val propertyWrapper: PropertyWrapper
) {
    override fun toString(): String {
        return propertyWrapper.toString()
    }

    val tracks = run {
        val list = mutableListOf(propertyWrapper)
        while (true) {
            list.add(list.last().to?.to ?: break)
        }
        list.map { p -> p.segment.tracked }.toTypedArray<Boolean>()
    }

    val trackIndex = tracks.indexOfFirst { it }.let { i ->
        if (i < 0) 0 else i
    }

    fun <T> match(
        node: T,
        transform: Transform<T>,
        trackNodes: MutableList<T> = ArrayList(tracks.size),
    ): T? {
        val trackTempNodes = matchTracks(node, transform, trackNodes) ?: return null
        return trackTempNodes[trackIndex]
    }

    fun <T> matchTracks(
        node: T,
        transform: Transform<T>,
        trackNodes: MutableList<T> = ArrayList(tracks.size),
    ): List<T>? {
        return propertyWrapper.matchTracks(node, transform, trackNodes)
    }

    val qfIdValue = propertyWrapper.segment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.left.value == "id" && e.operator.value == CompareOperator.Equal && e.right is ValueExpression.StringLiteral) {
            e.right.value
        } else {
            null
        }
    }

    val qfVidValue = propertyWrapper.segment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.left.value == "vid" && e.operator.value == CompareOperator.Equal && e.right is ValueExpression.StringLiteral) {
            e.right.value
        } else {
            null
        }
    }

    val qfTextValue = propertyWrapper.segment.expressions.firstOrNull().let { e ->
        if (e is BinaryExpression && e.left.value == "text" && (e.operator.value == CompareOperator.Equal || e.operator.value == CompareOperator.Start || e.operator.value == CompareOperator.Include || e.operator.value == CompareOperator.End) && e.right is ValueExpression.StringLiteral) {
            e.right.value
        } else {
            null
        }
    }

    val canQf = qfIdValue != null || qfVidValue != null || qfTextValue != null

    // 主动查询
    val isMatchRoot = propertyWrapper.segment.expressions.firstOrNull().let { e ->
        e is BinaryExpression && e.left.value == "depth" && e.operator.value == CompareOperator.Equal && e.right.value == 0
    }

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
        if (connectKeys.contains(ConnectOperator.BeforeBrother.key)) {
            return@run true
        }
        if (connectKeys.contains(ConnectOperator.AfterBrother.key)) {
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
    arrayOf("index", "parent")
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

