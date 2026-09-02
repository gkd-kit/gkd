package li.gkd.selector.property

import li.gkd.selector.SelectorMethod
import li.gkd.selector.MatchContext
import li.gkd.selector.SelectorType

internal enum class BuiltinScope {
    Boolean,
    Int,
    String,
    Context,
    Global,
}

private enum class BuiltinMethodId {
    BooleanToInt,
    BooleanOr,
    BooleanAnd,
    BooleanNot,
    BooleanIfElse,
    IntToString,
    IntPlus,
    IntMinus,
    IntTimes,
    IntDiv,
    IntRem,
    IntMore,
    IntMoreEqual,
    IntLess,
    IntLessEqual,
    StringGet,
    StringAt,
    StringSubstring,
    StringToInt,
    StringIndexOf,
    ContextGetPrev,
    GlobalEqual,
    GlobalNotEqual,
}

private class FixedSignature(
    val params: List<BuiltinScope>,
    val returnType: BuiltinScope,
)

private class BuiltinMethod(
    val id: BuiltinMethodId,
    val receiver: BuiltinScope,
    val name: String,
    val signatures: List<FixedSignature> = emptyList(),
    val homogeneousArity: Int? = null,
    val homogeneousReturnsBoolean: Boolean = false,
)

internal data class BuiltinTypeSet(
    val booleanType: SelectorType,
    val intType: SelectorType,
    val stringType: SelectorType,
    val contextType: SelectorType,
    val nodeType: SelectorType,
) {
    val homogeneousTypes: List<SelectorType>
        get() = listOf(booleanType, intType, stringType, nodeType, contextType)

    fun get(scope: BuiltinScope): SelectorType = when (scope) {
        BuiltinScope.Boolean -> booleanType
        BuiltinScope.Int -> intType
        BuiltinScope.String -> stringType
        BuiltinScope.Context -> contextType
        BuiltinScope.Global -> error("Global is not a value type")
    }
}

internal sealed interface BuiltinInvocation {
    data object Unsupported : BuiltinInvocation

    data class Value(val value: Any?) : BuiltinInvocation
}

internal object BuiltinMembers {
    private val methods: List<BuiltinMethod> by lazy {
        listOf(
            BuiltinMethod(
                BuiltinMethodId.BooleanToInt,
                BuiltinScope.Boolean,
                "toInt",
                signatures = listOf(FixedSignature(emptyList(), BuiltinScope.Int)),
            ),
            BuiltinMethod(
                BuiltinMethodId.BooleanOr,
                BuiltinScope.Boolean,
                "or",
                signatures = listOf(
                    FixedSignature(listOf(BuiltinScope.Boolean), BuiltinScope.Boolean),
                ),
            ),
            BuiltinMethod(
                BuiltinMethodId.BooleanAnd,
                BuiltinScope.Boolean,
                "and",
                signatures = listOf(
                    FixedSignature(listOf(BuiltinScope.Boolean), BuiltinScope.Boolean),
                ),
            ),
            BuiltinMethod(
                BuiltinMethodId.BooleanNot,
                BuiltinScope.Boolean,
                "not",
                signatures = listOf(FixedSignature(emptyList(), BuiltinScope.Boolean)),
            ),
            BuiltinMethod(
                BuiltinMethodId.BooleanIfElse,
                BuiltinScope.Boolean,
                "ifElse",
                homogeneousArity = 2,
            ),
            BuiltinMethod(
                BuiltinMethodId.IntToString,
                BuiltinScope.Int,
                "toString",
                signatures = listOf(
                    FixedSignature(emptyList(), BuiltinScope.String),
                    FixedSignature(listOf(BuiltinScope.Int), BuiltinScope.String),
                ),
            ),
            intMethod(BuiltinMethodId.IntPlus, "plus", BuiltinScope.Int),
            intMethod(BuiltinMethodId.IntMinus, "minus", BuiltinScope.Int),
            intMethod(BuiltinMethodId.IntTimes, "times", BuiltinScope.Int),
            intMethod(BuiltinMethodId.IntDiv, "div", BuiltinScope.Int),
            intMethod(BuiltinMethodId.IntRem, "rem", BuiltinScope.Int),
            intMethod(BuiltinMethodId.IntMore, "more", BuiltinScope.Boolean),
            intMethod(BuiltinMethodId.IntMoreEqual, "moreEqual", BuiltinScope.Boolean),
            intMethod(BuiltinMethodId.IntLess, "less", BuiltinScope.Boolean),
            intMethod(BuiltinMethodId.IntLessEqual, "lessEqual", BuiltinScope.Boolean),
            stringMethod(BuiltinMethodId.StringGet, "get", listOf(BuiltinScope.Int), BuiltinScope.String),
            stringMethod(BuiltinMethodId.StringAt, "at", listOf(BuiltinScope.Int), BuiltinScope.String),
            BuiltinMethod(
                BuiltinMethodId.StringSubstring,
                BuiltinScope.String,
                "substring",
                signatures = listOf(
                    FixedSignature(listOf(BuiltinScope.Int), BuiltinScope.String),
                    FixedSignature(listOf(BuiltinScope.Int, BuiltinScope.Int), BuiltinScope.String),
                ),
            ),
            BuiltinMethod(
                BuiltinMethodId.StringToInt,
                BuiltinScope.String,
                "toInt",
                signatures = listOf(
                    FixedSignature(emptyList(), BuiltinScope.Int),
                    FixedSignature(listOf(BuiltinScope.Int), BuiltinScope.Int),
                ),
            ),
            BuiltinMethod(
                BuiltinMethodId.StringIndexOf,
                BuiltinScope.String,
                "indexOf",
                signatures = listOf(
                    FixedSignature(listOf(BuiltinScope.String), BuiltinScope.Int),
                    FixedSignature(
                        listOf(BuiltinScope.String, BuiltinScope.Int),
                        BuiltinScope.Int,
                    ),
                ),
            ),
            BuiltinMethod(
                BuiltinMethodId.ContextGetPrev,
                BuiltinScope.Context,
                "getPrev",
                signatures = listOf(
                    FixedSignature(listOf(BuiltinScope.Int), BuiltinScope.Context),
                ),
            ),
            BuiltinMethod(
                BuiltinMethodId.GlobalEqual,
                BuiltinScope.Global,
                "equal",
                homogeneousArity = 2,
                homogeneousReturnsBoolean = true,
            ),
            BuiltinMethod(
                BuiltinMethodId.GlobalNotEqual,
                BuiltinScope.Global,
                "notEqual",
                homogeneousArity = 2,
                homogeneousReturnsBoolean = true,
            ),
        )
    }

    private fun intMethod(
        id: BuiltinMethodId,
        name: String,
        returnType: BuiltinScope,
    ): BuiltinMethod = BuiltinMethod(
        id,
        BuiltinScope.Int,
        name,
        signatures = listOf(FixedSignature(listOf(BuiltinScope.Int), returnType)),
    )

    private fun stringMethod(
        id: BuiltinMethodId,
        name: String,
        params: List<BuiltinScope>,
        returnType: BuiltinScope,
    ): BuiltinMethod = BuiltinMethod(
        id,
        BuiltinScope.String,
        name,
        signatures = listOf(FixedSignature(params, returnType)),
    )

    fun methodInfos(scope: BuiltinScope, types: BuiltinTypeSet): List<SelectorMethod> = methods
        .asSequence()
        .filter { it.receiver == scope }
        .flatMap { method ->
            method.signatures.asSequence().map { signature ->
                SelectorMethod(
                    method.name,
                    types.get(signature.returnType),
                    signature.params.map(types::get),
                )
            } + method.homogeneousArity?.let { arity ->
                types.homogeneousTypes.asSequence().map { type ->
                    SelectorMethod(
                        method.name,
                        if (method.homogeneousReturnsBoolean) types.booleanType else type,
                        List(arity) { type },
                    )
                }
            }.orEmpty()
        }
        .toList()

    fun readProperty(receiver: Any, name: String): BuiltinInvocation =
        if (receiver is CharSequence && name == "length") {
            BuiltinInvocation.Value(receiver.length)
        } else {
            BuiltinInvocation.Unsupported
        }

    fun invoke(
        receiver: Any,
        name: String,
        arguments: List<Any?>,
        global: Boolean = false,
    ): BuiltinInvocation {
        val scope = if (global) BuiltinScope.Global else receiver.scope()
            ?: return BuiltinInvocation.Unsupported
        var hasNamedMethod = false
        for (method in methods) {
            if (method.receiver != scope || method.name != name) continue
            hasNamedMethod = true
            if (method.accepts(arguments)) {
                return BuiltinInvocation.Value(method.evaluate(receiver, arguments))
            }
        }
        return if (hasNamedMethod) {
            BuiltinInvocation.Value(null)
        } else {
            BuiltinInvocation.Unsupported
        }
    }

    fun isShortCircuitOr(name: String, receiver: Any?): Boolean =
        name == "or" && receiver is Boolean

    fun isShortCircuitAnd(name: String, receiver: Any?): Boolean =
        name == "and" && receiver is Boolean

    fun isIfElse(name: String, receiver: Any?, argumentCount: Int): Boolean =
        name == "ifElse" && receiver is Boolean && argumentCount == 2

    private fun BuiltinMethod.accepts(arguments: List<Any?>): Boolean {
        if (homogeneousArity != null) {
            if (arguments.size != homogeneousArity) return false
            if (receiver == BuiltinScope.Global) return true
            return arguments.all { it == null || it.scope() != null }
        }
        return signatures.any { signature ->
            signature.params.size == arguments.size &&
                    signature.params.indices.all { index ->
                        arguments[index]?.scope() == signature.params[index]
                    }
        }
    }

    private fun BuiltinMethod.evaluate(receiver: Any, args: List<Any?>): Any? = when (id) {
        BuiltinMethodId.BooleanToInt -> if (receiver as Boolean) 1 else 0
        BuiltinMethodId.BooleanOr -> receiver as Boolean || args[0] as Boolean
        BuiltinMethodId.BooleanAnd -> receiver as Boolean && args[0] as Boolean
        BuiltinMethodId.BooleanNot -> !(receiver as Boolean)
        BuiltinMethodId.BooleanIfElse -> if (receiver as Boolean) args[0] else args[1]
        BuiltinMethodId.IntToString -> when (args.size) {
            0 -> (receiver as Int).toString()
            else -> (args[0] as Int).takeIf { it in 2..36 }?.let((receiver as Int)::toString)
        }

        BuiltinMethodId.IntPlus -> (receiver as Int) + (args[0] as Int)
        BuiltinMethodId.IntMinus -> (receiver as Int) - (args[0] as Int)
        BuiltinMethodId.IntTimes -> (receiver as Int) * (args[0] as Int)
        BuiltinMethodId.IntDiv ->
            (args[0] as Int).takeIf { it != 0 }?.let { (receiver as Int) / it }

        BuiltinMethodId.IntRem ->
            (args[0] as Int).takeIf { it != 0 }?.let { (receiver as Int) % it }

        BuiltinMethodId.IntMore -> (receiver as Int) > (args[0] as Int)
        BuiltinMethodId.IntMoreEqual -> (receiver as Int) >= (args[0] as Int)
        BuiltinMethodId.IntLess -> (receiver as Int) < (args[0] as Int)
        BuiltinMethodId.IntLessEqual -> (receiver as Int) <= (args[0] as Int)
        BuiltinMethodId.StringGet -> (receiver as CharSequence)
            .getOrNull(args[0] as Int)
            ?.toString()

        BuiltinMethodId.StringAt -> {
            val target = receiver as CharSequence
            val index = args[0] as Int
            target.getOrNull(if (index < 0) target.length + index else index)?.toString()
        }

        BuiltinMethodId.StringSubstring -> {
            val target = receiver as CharSequence
            val start = args[0] as Int
            if (start < 0) return null
            if (start >= target.length) return ""
            val end = (args.getOrNull(1) as? Int) ?: target.length
            if (end < start) return null
            target.substring(start, end.coerceAtMost(target.length))
        }

        BuiltinMethodId.StringToInt -> {
            val target = receiver as CharSequence
            val radix = (args.firstOrNull() as? Int) ?: 10
            if (radix !in 2..36) return null
            target.toString().toIntOrNull(radix)
        }

        BuiltinMethodId.StringIndexOf -> (receiver as CharSequence).indexOf(
            (args[0] as CharSequence).toString(),
            (args.getOrNull(1) as? Int) ?: 0,
        )

        BuiltinMethodId.ContextGetPrev ->
            (receiver as MatchContext<*>).getPrev(args[0] as Int)

        BuiltinMethodId.GlobalEqual -> comparePrimitiveValue(args[0], args[1])
        BuiltinMethodId.GlobalNotEqual -> !comparePrimitiveValue(args[0], args[1])
    }

    private fun Any.scope(): BuiltinScope? = when (this) {
        is Boolean -> BuiltinScope.Boolean
        is Int -> BuiltinScope.Int
        is CharSequence -> BuiltinScope.String
        is MatchContext<*> -> BuiltinScope.Context
        else -> null
    }
}
