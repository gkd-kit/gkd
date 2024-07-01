package li.songe.selector

import kotlin.js.JsExport

fun escapeString(value: String, wrapChar: Char = '"'): String {
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

@JsExport
class DefaultTypeInfo(
    val booleanType: TypeInfo,
    val intType: TypeInfo,
    val stringType: TypeInfo,
    val contextType: TypeInfo,
    val nodeType: TypeInfo,
)

@JsExport
fun initDefaultTypeInfo(): DefaultTypeInfo {
    val booleanType = TypeInfo(PrimitiveType.BooleanType)
    val intType = TypeInfo(PrimitiveType.IntType)
    val stringType = TypeInfo(PrimitiveType.StringType)

    intType.methods = arrayOf(
        MethodInfo("toString", stringType),
        MethodInfo("toString", stringType, arrayOf(intType)),
        MethodInfo("plus", intType, arrayOf(intType)),
        MethodInfo("minus", intType, arrayOf(intType)),
        MethodInfo("times", intType, arrayOf(intType)),
        MethodInfo("div", intType, arrayOf(intType)),
        MethodInfo("rem", intType, arrayOf(intType)),
    )
    stringType.props = arrayOf(
        PropInfo("length", intType),
    )
    stringType.methods = arrayOf(
        MethodInfo("get", stringType, arrayOf(intType)),
        MethodInfo("at", stringType, arrayOf(intType)),
        MethodInfo("substring", stringType, arrayOf(intType, intType)),
        MethodInfo("toInt", intType),
        MethodInfo("toInt", intType, arrayOf(intType)),
        MethodInfo("indexOf", intType, arrayOf(stringType)),
        MethodInfo("indexOf", intType, arrayOf(stringType, intType)),
    )

    val contextType = TypeInfo(PrimitiveType.ObjectType("context"))
    val nodeType = TypeInfo(PrimitiveType.ObjectType("node"))

    nodeType.props = arrayOf(
        PropInfo("_id", intType),
        PropInfo("_pid", intType),

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
    nodeType.methods = arrayOf(
        MethodInfo("getChild", nodeType, arrayOf(intType)),
    )
    contextType.methods = arrayOf(*nodeType.methods)
    contextType.props = arrayOf(*nodeType.props)
    return DefaultTypeInfo(
        booleanType = booleanType,
        intType = intType,
        stringType = stringType,
        contextType = contextType,
        nodeType = nodeType
    )
}

@JsExport
fun getIntInvoke(target: Int, name: String, args: List<Any?>): Any? {
    return when (name) {
        "plus" -> {
            target + (args.getIntOrNull() ?: return null)
        }

        "minus" -> {
            target - (args.getIntOrNull() ?: return null)
        }

        "times" -> {
            target * (args.getIntOrNull() ?: return null)
        }

        "div" -> {
            target / (args.getIntOrNull()?.also { if (it == 0) return null } ?: return null)
        }

        "rem" -> {
            target % (args.getIntOrNull()?.also { if (it == 0) return null } ?: return null)
        }

        else -> null
    }
}


internal fun List<Any?>.getIntOrNull(i: Int = 0): Int? {
    val v = getOrNull(i)
    if (v is Int) return v
    return null
}

@JsExport
fun getStringInvoke(target: String, name: String, args: List<Any?>): Any? {
    return getCharSequenceInvoke(target, name, args)
}

fun getCharSequenceInvoke(target: CharSequence, name: String, args: List<Any?>): Any? {

    return when (name) {
        "get" -> {
            target.getOrNull(args.getIntOrNull() ?: return null).toString()
        }

        "at" -> {
            val i = args.getIntOrNull() ?: return null
            if (i < 0) {
                target.getOrNull(target.length + i).toString()
            } else {
                target.getOrNull(i).toString()
            }
        }

        "substring" -> {
            when (args.size) {
                1 -> {
                    val start = args.getIntOrNull() ?: return null
                    if (start < 0) return null
                    if (start >= target.length) return ""
                    target.substring(
                        start,
                        target.length
                    )
                }

                2 -> {
                    val start = args.getIntOrNull() ?: return null
                    if (start < 0) return null
                    if (start >= target.length) return ""
                    val end = args.getIntOrNull(1) ?: return null
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
                val radix = args.getIntOrNull() ?: return null
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
                    val startIndex = args.getIntOrNull(1) ?: return null
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
