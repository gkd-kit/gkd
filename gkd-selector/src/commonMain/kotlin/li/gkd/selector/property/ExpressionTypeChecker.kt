package li.gkd.selector.property

import li.gkd.selector.SelectorMethod
import li.gkd.selector.SelectorType
import li.gkd.selector.SelectorTypeErrorKind
import li.gkd.selector.SelectorTypeKind
import li.gkd.selector.TypeCheckFailure
import li.gkd.selector.syntax.SelectorPrinter

internal class TypeCheckCollector(
    val globalType: SelectorType,
    private val limit: Int,
) {
    val failures: List<TypeCheckFailure>
        field = mutableListOf<TypeCheckFailure>()

    val isFull: Boolean
        get() = failures.size >= limit

    val collectsAll: Boolean
        get() = limit > 1

    fun report(failure: TypeCheckFailure) {
        if (!isFull) failures.add(failure)
    }
}

private sealed interface TypeFrame

private class MemberFrame(
    val expression: ValueExpression.MemberExpression,
) : TypeFrame

private class CallFrame(
    val expression: ValueExpression.CallExpression,
    var receiver: TypeResolution? = null,
    var argumentIndex: Int = -1,
    val arguments: Array<TypeInferenceResult?> = arrayOfNulls(expression.arguments.size),
) : TypeFrame

internal sealed interface TypeInferenceResult {
    data class Known(val type: SelectorTypeKind) : TypeInferenceResult

    data object NullValue : TypeInferenceResult

    data object Invalid : TypeInferenceResult
}

private sealed interface TypeResolution {
    data class Known(val type: SelectorType) : TypeResolution

    data object Invalid : TypeResolution
}

internal fun ValueExpression.inferType(
    collector: TypeCheckCollector,
): TypeInferenceResult = when (this) {
    is ValueExpression.LiteralExpression -> literalType
        ?.let(TypeInferenceResult::Known)
        ?: TypeInferenceResult.NullValue

    is ValueExpression.Variable -> when (val resolution = inferVariableType(collector)) {
        is TypeResolution.Known -> TypeInferenceResult.Known(resolution.type.type)
        TypeResolution.Invalid -> TypeInferenceResult.Invalid
    }
}

private fun ValueExpression.Variable.inferVariableType(
    collector: TypeCheckCollector,
): TypeResolution {
    val stack = mutableListOf<TypeFrame>()
    var current: ValueExpression.Variable = this
    var result: TypeResolution

    while (true) {
        when (current) {
            is ValueExpression.Identifier -> {
                result = collector.globalType.props
                    .firstOrNull { it.name == current.name }
                    ?.type
                    ?.let(TypeResolution::Known)
                    ?: run {
                        collector.report(
                            current.typeFailure(SelectorTypeErrorKind.UnknownIdentifier),
                        )
                        if (collector.isFull) return TypeResolution.Invalid
                        TypeResolution.Invalid
                    }
            }

            is ValueExpression.MemberExpression -> {
                stack.add(MemberFrame(current))
                current = current.object0
                continue
            }

            is ValueExpression.CallExpression -> {
                if (current.callee is ValueExpression.CallExpression) {
                    error("Unsupported nested call")
                }
                val frame = CallFrame(current)
                stack.add(frame)
                val memberCallee = current.callee as? ValueExpression.MemberExpression
                if (memberCallee != null) {
                    current = memberCallee.object0
                    continue
                }
                frame.receiver = TypeResolution.Known(collector.globalType)
                nextVariableArgument(frame)?.let { argument ->
                    current = argument
                    continue
                }
                stack.removeAt(stack.lastIndex)
                result = finishCall(frame, collector)
                if (collector.isFull) return TypeResolution.Invalid
            }
        }

        while (true) {
            if (stack.isEmpty()) return result
            when (val frame = stack.last()) {
                is MemberFrame -> {
                    stack.removeAt(stack.lastIndex)
                    result = when (val receiver = result) {
                        is TypeResolution.Known -> receiver.type.props
                            .firstOrNull { it.name == frame.expression.property }
                            ?.type
                            ?.let(TypeResolution::Known)
                            ?: run {
                                collector.report(
                                    frame.expression.typeFailure(
                                        SelectorTypeErrorKind.UnknownMember,
                                    ),
                                )
                                if (collector.isFull) return TypeResolution.Invalid
                                TypeResolution.Invalid
                            }

                        TypeResolution.Invalid -> TypeResolution.Invalid
                    }
                }

                is CallFrame -> {
                    if (frame.receiver == null) {
                        frame.receiver = result
                    } else {
                        frame.arguments[frame.argumentIndex] = when (val argument = result) {
                            is TypeResolution.Known -> TypeInferenceResult.Known(argument.type.type)
                            TypeResolution.Invalid -> TypeInferenceResult.Invalid
                        }
                    }
                    nextVariableArgument(frame)?.let { argument ->
                        current = argument
                        break
                    }
                    stack.removeAt(stack.lastIndex)
                    result = finishCall(frame, collector)
                    if (collector.isFull) return TypeResolution.Invalid
                }
            }
        }
    }
}

private fun nextVariableArgument(frame: CallFrame): ValueExpression.Variable? {
    var index = frame.argumentIndex + 1
    while (index < frame.expression.arguments.size) {
        when (val argument = frame.expression.arguments[index]) {
            is ValueExpression.LiteralExpression -> frame.arguments[index] = argument.literalType
                ?.let(TypeInferenceResult::Known)
                ?: TypeInferenceResult.NullValue

            is ValueExpression.Variable -> {
                frame.argumentIndex = index
                return argument
            }
        }
        index++
    }
    frame.argumentIndex = frame.expression.arguments.size
    return null
}

private fun finishCall(
    frame: CallFrame,
    collector: TypeCheckCollector,
): TypeResolution {
    val expression = frame.expression
    val methods = resolveMethods(frame, collector) ?: return TypeResolution.Invalid
    val checks = methods.map { method ->
        method to parameterMismatches(
            call = expression,
            method = method,
            arguments = frame.arguments,
            limit = if (collector.collectsAll) Int.MAX_VALUE else 1,
        )
    }
    val matchingMethods = checks.filter { (_, failures) -> failures.isEmpty() }
        .map { (method) -> method }
    if (matchingMethods.isEmpty()) {
        val failures = if (collector.collectsAll) {
            checks.minBy { (_, failures) -> failures.size }.second
        } else {
            checks.last().second
        }
        failures.forEach { failure ->
            collector.report(failure)
            if (collector.isFull) return TypeResolution.Invalid
        }
        return TypeResolution.Invalid
    }
    if (
        frame.arguments.any { it === TypeInferenceResult.Invalid } &&
        matchingMethods.drop(1).any { it.returnType !== matchingMethods.first().returnType }
    ) {
        return TypeResolution.Invalid
    }
    return TypeResolution.Known(matchingMethods.first().returnType)
}

private fun resolveMethods(
    frame: CallFrame,
    collector: TypeCheckCollector,
): List<SelectorMethod>? {
    val expression = frame.expression
    val methods = when (val callee = expression.callee) {
        is ValueExpression.CallExpression -> error("Unsupported nested call")
        is ValueExpression.Identifier -> collector.globalType.methods.filter { it.name == callee.name }
        is ValueExpression.MemberExpression -> when (val receiver = frame.receiver) {
            is TypeResolution.Known -> receiver.type.methods.filter { it.name == callee.property }
            TypeResolution.Invalid -> return null
            null -> error("Missing call receiver")
        }
    }
    if (methods.isEmpty()) {
        collector.report(
            expression.callee.typeFailure(SelectorTypeErrorKind.UnknownMethod),
        )
        return null
    }
    val matchingArity = methods.filter { it.params.size == expression.arguments.size }
    if (matchingArity.isEmpty()) {
        collector.report(
            expression.typeFailure(
                kind = SelectorTypeErrorKind.ArgumentCountMismatch,
                expected = methods.map { it.params.size }.distinct().sorted().joinToString(" or "),
                actual = expression.arguments.size.toString(),
            ),
        )
        return null
    }
    return matchingArity
}

private fun parameterMismatches(
    call: ValueExpression.CallExpression,
    method: SelectorMethod,
    arguments: Array<TypeInferenceResult?>,
    limit: Int,
): List<TypeCheckFailure> {
    val failures = mutableListOf<TypeCheckFailure>()
    call.arguments.indices.forEach { index ->
        val actualType = when (val argument = arguments[index]) {
            is TypeInferenceResult.Known -> argument.type
            TypeInferenceResult.NullValue, TypeInferenceResult.Invalid -> return@forEach
            null -> error("Missing call argument type")
        }
        if (actualType != method.params[index].type) {
            val argument = call.arguments[index]
            failures.add(
                TypeCheckFailure(
                    kind = SelectorTypeErrorKind.ArgumentTypeMismatch,
                    positionValue = argument,
                    expression = SelectorPrinter.render(argument),
                    expected = method.params[index].displayName,
                    actual = actualType.displayName,
                ),
            )
            if (failures.size >= limit) return failures
        }
    }
    return failures
}

private fun ValueExpression.typeFailure(
    kind: SelectorTypeErrorKind,
    expected: String? = null,
    actual: String? = null,
): TypeCheckFailure = TypeCheckFailure(
    kind = kind,
    positionValue = this,
    expression = SelectorPrinter.render(this),
    expected = expected,
    actual = actual,
)
