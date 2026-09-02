package li.gkd.selector.property

import li.gkd.selector.MatchContext
import li.gkd.selector.NodeAdapter

private sealed interface ValueEvaluationFrame {
    class Member(val property: String) : ValueEvaluationFrame

    class Call(
        val expression: ValueExpression.CallExpression,
        var state: Int,
        var receiver: Any? = null,
        var argumentIndex: Int = 0,
        val arguments: MutableList<Any?> = mutableListOf(),
    ) : ValueEvaluationFrame
}

private const val EXPECT_RECEIVER = 0
private const val EXPECT_ARGUMENT = 1
private const val EXPECT_SELECTED_ARGUMENT = 2

internal fun <T : Any> ValueExpression.evaluate(
    context: MatchContext<T>,
    adapter: NodeAdapter<T>,
): Any? {
    when (this) {
        is ValueExpression.LiteralExpression -> return value
        is ValueExpression.Identifier -> return resolve(context, adapter)
        is ValueExpression.MemberExpression,
        is ValueExpression.CallExpression -> Unit
    }
    val stack = mutableListOf<ValueEvaluationFrame>()
    var current: ValueExpression = this
    var result: Any?

    while (true) {
        when (current) {
            is ValueExpression.LiteralExpression -> result = current.value
            is ValueExpression.Identifier -> result = current.resolve(context, adapter)
            is ValueExpression.MemberExpression -> {
                stack.add(ValueEvaluationFrame.Member(current.property))
                current = current.object0
                continue
            }

            is ValueExpression.CallExpression -> {
                when (val callee = current.callee) {
                    is ValueExpression.CallExpression -> result = null
                    is ValueExpression.Identifier -> {
                        val frame = ValueEvaluationFrame.Call(
                            expression = current,
                            state = EXPECT_ARGUMENT,
                            receiver = context,
                        )
                        stack.add(frame)
                        if (current.arguments.isEmpty()) {
                            result = finishCall(frame, adapter)
                            stack.removeAt(stack.lastIndex)
                        } else {
                            current = current.arguments.first()
                            continue
                        }
                    }

                    is ValueExpression.MemberExpression -> {
                        stack.add(
                            ValueEvaluationFrame.Call(
                                expression = current,
                                state = EXPECT_RECEIVER,
                            ),
                        )
                        current = callee.object0
                        continue
                    }
                }
            }
        }

        while (true) {
            if (stack.isEmpty()) return result
            when (val frame = stack.last()) {
                is ValueEvaluationFrame.Member -> {
                    stack.removeAt(stack.lastIndex)
                    result = result?.let { readProperty(it, frame.property, adapter) }
                }

                is ValueEvaluationFrame.Call -> when (frame.state) {
                    EXPECT_RECEIVER -> {
                        if (result == null) {
                            stack.removeAt(stack.lastIndex)
                            continue
                        }
                        frame.receiver = result
                        val callee = frame.expression.callee as ValueExpression.MemberExpression
                        when {
                            BuiltinMembers.isShortCircuitOr(callee.property, result) &&
                                    result == true -> {
                                stack.removeAt(stack.lastIndex)
                                result = true
                            }

                            BuiltinMembers.isShortCircuitAnd(callee.property, result) &&
                                    result == false -> {
                                stack.removeAt(stack.lastIndex)
                                result = false
                            }

                            BuiltinMembers.isIfElse(
                                callee.property,
                                result,
                                frame.expression.arguments.size,
                            ) -> {
                                frame.state = EXPECT_SELECTED_ARGUMENT
                                val selectedIndex = if (result == true) 0 else 1
                                current = frame.expression.arguments[selectedIndex]
                                break
                            }

                            frame.expression.arguments.isEmpty() -> {
                                stack.removeAt(stack.lastIndex)
                                result = finishCall(frame, adapter)
                            }

                            else -> {
                                frame.state = EXPECT_ARGUMENT
                                current = frame.expression.arguments.first()
                                break
                            }
                        }
                    }

                    EXPECT_SELECTED_ARGUMENT -> {
                        stack.removeAt(stack.lastIndex)
                    }

                    EXPECT_ARGUMENT -> {
                        val callee = frame.expression.callee
                        val allowsNull = callee is ValueExpression.Identifier &&
                                callee.role == ValueExpression.IdentifierRole.NullTolerantFunction
                        if (result == null && !allowsNull) {
                            stack.removeAt(stack.lastIndex)
                            continue
                        }
                        frame.arguments.add(result)
                        frame.argumentIndex++
                        if (frame.argumentIndex < frame.expression.arguments.size) {
                            current = frame.expression.arguments[frame.argumentIndex]
                            break
                        }
                        stack.removeAt(stack.lastIndex)
                        result = finishCall(frame, adapter)
                    }

                    else -> error("Unknown value evaluation state")
                }
            }
        }
    }
}

private fun <T : Any> ValueExpression.Identifier.resolve(
    context: MatchContext<T>,
    adapter: NodeAdapter<T>,
): Any? = when {
    role == ValueExpression.IdentifierRole.Previous -> context.prev
    role == ValueExpression.IdentifierRole.Current -> context.current
    else -> adapter.getAttr(context.current, name)
}

private fun <T : Any> readProperty(
    receiver: Any,
    property: String,
    adapter: NodeAdapter<T>,
): Any? {
    if (receiver is MatchContext<*>) {
        return when (property) {
            "prev" -> receiver.prev
            "current" -> receiver.current
            else -> adapter.getAttr(receiver.current, property)
        }
    }
    return when (val result = BuiltinMembers.readProperty(receiver, property)) {
        is BuiltinInvocation.Value -> result.value
        BuiltinInvocation.Unsupported -> adapter.getAttr(receiver, property)
    }
}

private fun <T : Any> finishCall(
    frame: ValueEvaluationFrame.Call,
    adapter: NodeAdapter<T>,
): Any? = when (val callee = frame.expression.callee) {
    is ValueExpression.CallExpression -> null
    is ValueExpression.Identifier -> invoke(
        receiver = frame.receiver ?: return null,
        name = callee.name,
        arguments = frame.arguments,
        adapter = adapter,
        global = true,
    )

    is ValueExpression.MemberExpression -> invoke(
        receiver = frame.receiver ?: return null,
        name = callee.property,
        arguments = frame.arguments,
        adapter = adapter,
    )
}

private fun <T : Any> invoke(
    receiver: Any,
    name: String,
    arguments: List<Any?>,
    adapter: NodeAdapter<T>,
    global: Boolean = false,
): Any? {
    if (global) {
        when (val result = BuiltinMembers.invoke(receiver, name, arguments, global = true)) {
            is BuiltinInvocation.Value -> return result.value
            BuiltinInvocation.Unsupported -> Unit
        }
    }
    when (val result = BuiltinMembers.invoke(receiver, name, arguments)) {
        is BuiltinInvocation.Value -> return result.value
        BuiltinInvocation.Unsupported -> Unit
    }
    if (arguments.any { it == null }) return null
    val target = if (receiver is MatchContext<*>) receiver.current else receiver
    return adapter.getInvoke(target, name, arguments.filterNotNull())
}
