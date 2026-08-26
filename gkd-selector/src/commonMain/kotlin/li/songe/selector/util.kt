@file:Suppress("unused")

package li.songe.selector

import kotlin.js.JsExport

internal fun escapeString(value: String, wrapChar: Char = '"'): String {
    val sb = StringBuilder(value.length + 2)
    sb.append(wrapChar)
    value.forEach { c ->
        val escapeChar = when (c) {
            wrapChar -> wrapChar
            '\n' -> 'n'
            '\r' -> 'r'
            '\t' -> 't'
            '\b' -> 'b'
            '\\' -> '\\'
            else -> null
        }
        if (escapeChar != null) {
            sb.append("\\" + escapeChar)
        } else {
            when (c.code) {
                in 0..0xf -> {
                    sb.append("\\x0" + c.code.toString(16))
                }

                in 0x10..0x1f -> {
                    sb.append("\\x" + c.code.toString(16))
                }

                else -> {
                    sb.append(c)
                }
            }
        }
    }
    sb.append(wrapChar)
    return sb.toString()
}

private const val REG_SPECIAL_STRING = "\\^$.?*|+()[]{}"
private fun getMatchValue(value: String, prefix: String, suffix: String): String? {
    if (value.startsWith(prefix) && value.endsWith(suffix) && value.length >= (prefix.length + suffix.length)) {
        for (i in prefix.length until value.length - suffix.length) {
            if (value[i] in REG_SPECIAL_STRING) {
                return null
            }
        }
        return value.subSequence(prefix.length, value.length - suffix.length).toString()
    }
    return null
}

internal fun optimizeMatchString(value: String): ((CharSequence) -> Boolean)? {
    getMatchValue(value, "(?is)", ".*")?.let { startsWithValue ->
        return { value -> value.startsWith(startsWithValue, ignoreCase = true) }
    }
    getMatchValue(value, "(?is).*", ".*")?.let { containsValue ->
        return { value -> value.contains(containsValue, ignoreCase = true) }
    }
    getMatchValue(value, "(?is).*", "")?.let { endsWithValue ->
        return { value -> value.endsWith(endsWithValue, ignoreCase = true) }
    }
    return null
}

internal inline fun <T> T?.whenNull(block: () -> Nothing): T {
    if (this == null) {
        block()
    }
    return this
}

@JsExport
class DefaultTypeInfo(
    val booleanType: TypeInfo,
    val intType: TypeInfo,
    val stringType: TypeInfo,
    val contextType: TypeInfo,
    val nodeType: TypeInfo,
    val globalType: TypeInfo
)

@JsExport
fun initDefaultTypeInfo(webField: Boolean = false): DefaultTypeInfo {
    val booleanType = TypeInfo(PrimitiveType.BooleanType)
    val intType = TypeInfo(PrimitiveType.IntType)
    val stringType = TypeInfo(PrimitiveType.StringType)
    val nodeType = TypeInfo(PrimitiveType.ObjectType("node"))
    val contextType = TypeInfo(PrimitiveType.ObjectType("context"))
    val globalType = TypeInfo(PrimitiveType.ObjectType("global"))

    fun buildMethods(name: String, returnType: TypeInfo, paramsSize: Int): List<MethodInfo> {
        return listOf(
            MethodInfo(name, returnType, List(paramsSize) { booleanType }),
            MethodInfo(name, returnType, List(paramsSize) { intType }),
            MethodInfo(name, returnType, List(paramsSize) { stringType }),
            MethodInfo(name, returnType, List(paramsSize) { nodeType }),
            MethodInfo(name, returnType, List(paramsSize) { contextType }),
        )
    }

    booleanType.methods = listOf(
        MethodInfo("toInt", intType),
        MethodInfo("or", booleanType, listOf(booleanType)),
        MethodInfo("and", booleanType, listOf(booleanType)),
        MethodInfo("not", booleanType),
    ) + buildMethods("ifElse", booleanType, 2)

    intType.methods = listOf(
        MethodInfo("toString", stringType),
        MethodInfo("toString", stringType, listOf(intType)),
        MethodInfo("plus", intType, listOf(intType)),
        MethodInfo("minus", intType, listOf(intType)),
        MethodInfo("times", intType, listOf(intType)),
        MethodInfo("div", intType, listOf(intType)),
        MethodInfo("rem", intType, listOf(intType)),
        MethodInfo("more", booleanType, listOf(intType)),
        MethodInfo("moreEqual", booleanType, listOf(intType)),
        MethodInfo("less", booleanType, listOf(intType)),
        MethodInfo("lessEqual", booleanType, listOf(intType)),
    )
    stringType.props = listOf(
        PropInfo("length", intType),
    )
    stringType.methods = listOf(
        MethodInfo("get", stringType, listOf(intType)),
        MethodInfo("at", stringType, listOf(intType)),
        MethodInfo("substring", stringType, listOf(intType)),
        MethodInfo("substring", stringType, listOf(intType, intType)),
        MethodInfo("toInt", intType),
        MethodInfo("toInt", intType, listOf(intType)),
        MethodInfo("indexOf", intType, listOf(stringType)),
        MethodInfo("indexOf", intType, listOf(stringType, intType)),
    )
    nodeType.props = listOf(
        * (if (webField) {
            arrayOf(
                PropInfo("_id", intType),
                PropInfo("_pid", intType),
            )
        } else {
            emptyArray()
        }),

        PropInfo("id", stringType),
        PropInfo("vid", stringType),
        PropInfo("name", stringType),
        PropInfo("text", stringType),
        PropInfo("desc", stringType),

        PropInfo("clickable", booleanType),
        PropInfo("focusable", booleanType),
        PropInfo("checkable", booleanType),
        PropInfo("checked", booleanType),
        PropInfo("editable", booleanType),
        PropInfo("longClickable", booleanType),
        PropInfo("visibleToUser", booleanType),

        PropInfo("left", intType),
        PropInfo("top", intType),
        PropInfo("right", intType),
        PropInfo("bottom", intType),
        PropInfo("width", intType),
        PropInfo("height", intType),

        PropInfo("childCount", intType),
        PropInfo("index", intType),
        PropInfo("depth", intType),

        PropInfo("parent", nodeType),
    )
    nodeType.methods = listOf(
        MethodInfo("getChild", nodeType, listOf(intType)),
    )
    contextType.methods = nodeType.methods + listOf(
        MethodInfo("getPrev", contextType, listOf(intType))
    )
    contextType.props = nodeType.props + listOf(
        PropInfo("prev", contextType),
        PropInfo("current", nodeType),
    )
    globalType.methods = contextType.methods +
            buildMethods("equal", booleanType, 2) +
            buildMethods("notEqual", booleanType, 2)

    globalType.props = contextType.props.toList()
    return DefaultTypeInfo(
        booleanType = booleanType,
        intType = intType,
        stringType = stringType,
        contextType = contextType,
        nodeType = nodeType,
        globalType = globalType
    )
}

@JsExport
fun getIntInvoke(target: Int, name: String, args: List<Any>): Any? {
    return when (name) {
        "plus" -> {
            target + args.getInt()
        }

        "minus" -> {
            target - args.getInt()
        }

        "times" -> {
            target * args.getInt()
        }

        "div" -> {
            target / args.getInt().also { if (it == 0) return null }
        }

        "rem" -> {
            target % args.getInt().also { if (it == 0) return null }
        }

        "more" -> {
            target > args.getInt()
        }

        "moreEqual" -> {
            target >= args.getInt()
        }

        "less" -> {
            target < args.getInt()
        }

        "lessEqual" -> {
            target <= args.getInt()
        }

        else -> null
    }
}


internal fun List<Any>.getInt(i: Int = 0) = get(i) as Int

@JsExport
fun getStringInvoke(target: String, name: String, args: List<Any>): Any? {
    return getCharSequenceInvoke(target, name, args)
}

@JsExport
fun getBooleanInvoke(target: Boolean, name: String, args: List<Any>): Any? {
    return when (name) {
        "toInt" -> if (target) 1 else 0
        "not" -> !target
        else -> null
    }
}

fun getCharSequenceInvoke(target: CharSequence, name: String, args: List<Any>): Any? {
    return when (name) {
        "get" -> {
            target.getOrNull(args.getInt()).toString()
        }

        "at" -> {
            val i = args.getInt()
            if (i < 0) {
                target.getOrNull(target.length + i).toString()
            } else {
                target.getOrNull(i).toString()
            }
        }

        "substring" -> {
            when (args.size) {
                1 -> {
                    val start = args.getInt()
                    if (start < 0) return null
                    if (start >= target.length) return ""
                    target.substring(
                        start,
                        target.length
                    )
                }

                2 -> {
                    val start = args.getInt()
                    if (start < 0) return null
                    if (start >= target.length) return ""
                    val end = args.getInt(1)
                    if (end < start) return null
                    target.substring(
                        start,
                        end.coerceAtMost(target.length)
                    )
                }

                else -> {
                    null
                }
            }
        }

        "toInt" -> when (args.size) {
            0 -> target.toString().toIntOrNull()
            1 -> {
                val radix = args.getInt()
                if (radix !in 2..36) {
                    return null
                }
                target.toString().toIntOrNull(radix)
            }

            else -> null
        }

        "indexOf" -> {
            when (args.size) {
                1 -> {
                    val str = args[0] as? CharSequence ?: return null
                    target.indexOf(str.toString())
                }

                2 -> {
                    val str = args[0] as? CharSequence ?: return null
                    val startIndex = args.getInt(1)
                    target.indexOf(str.toString(), startIndex)
                }

                else -> null
            }
        }

        else -> null
    }
}

@JsExport
fun getStringAttr(target: String, name: String): Any? {
    return getCharSequenceAttr(target, name)
}

fun getCharSequenceAttr(target: CharSequence, name: String): Any? {
    return when (name) {
        "length" -> target.length
        else -> null
    }
}

// example
// id="com.lptiyu.tanke:id/ab1"
// id="com.lptiyu.tanke:id/ab2"
internal fun CharSequence.contentReversedEquals(other: CharSequence): Boolean {
    if (this === other) return true
    if (this.length != other.length) return false
    for (i in this.length - 1 downTo 0) {
        if (this[i] != other[i]) return false
    }
    return true
}

internal fun comparePrimitiveValue(left: Any?, right: Any?): Boolean {
    return if (left is CharSequence && right is CharSequence) {
        left.contentReversedEquals(right)
    } else {
        left == right
    }
}