package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed class ValueExpression(open val value: Any?, open val type: String) : Position {
    override fun stringify() = value.toString()
    internal abstract fun <T> getAttr(node: T, transform: Transform<T>): Any?
    abstract val properties: Array<String>
    abstract val methods: Array<String>

    sealed class Variable(
        override val value: String,
    ) : ValueExpression(value, "var")

    data class Identifier internal constructor(
        override val start: Int,
        override val value: String,
    ) : Variable(value) {
        override val end = start + value.length
        override fun <T> getAttr(node: T, transform: Transform<T>): Any? {
            return transform.getAttr(node, value)
        }

        override val properties: Array<String>
            get() = arrayOf(value)
        override val methods: Array<String>
            get() = emptyArray()
    }

    data class MemberExpression internal constructor(
        override val start: Int,
        override val end: Int,
        val object0: Variable,
        val property: String,
    ) : Variable(value = "${object0.stringify()}.$property") {
        override fun <T> getAttr(node: T, transform: Transform<T>): Any? {
            return transform.getAttr(object0.getAttr(node, transform), property)
        }

        override val properties: Array<String>
            get() = arrayOf(*object0.properties, property)
        override val methods: Array<String>
            get() = object0.methods
    }

    data class CallExpression internal constructor(
        override val start: Int,
        override val end: Int,
        val callee: Variable,
        val arguments: List<ValueExpression>,
    ) : Variable(
        value = "${callee.stringify()}(${arguments.joinToString(",") { it.stringify() }})",
    ) {

        override fun <T> getAttr(node: T, transform: Transform<T>): Any? {
            return when (callee) {
                is CallExpression -> {
                    null
                }

                is Identifier -> {
                    transform.getInvoke(
                        node,
                        callee.value,
                        arguments.map { it.getAttr(node, transform) }
                    )
                }

                is MemberExpression -> {
                    transform.getInvoke(
                        callee.object0.getAttr(node, transform),
                        callee.property,
                        arguments.map { it.getAttr(node, transform) }
                    )
                }
            }
        }

        override val properties: Array<String>
            get() = callee.properties.toMutableList()
                .plus(arguments.flatMap { it.properties.toList() })
                .toTypedArray()
        override val methods: Array<String>
            get() = when (callee) {
                is CallExpression -> callee.methods
                is Identifier -> arrayOf(callee.value)
                is MemberExpression -> arrayOf(*callee.object0.methods, callee.property)
            }.toMutableList().plus(arguments.flatMap { it.methods.toList() })
                .toTypedArray()
    }

    sealed class LiteralExpression(
        override val value: Any?,
        override val type: String,
    ) : ValueExpression(value, type) {
        override fun <T> getAttr(node: T, transform: Transform<T>) = value

        override val properties: Array<String>
            get() = emptyArray()
        override val methods: Array<String>
            get() = emptyArray()
    }

    data class NullLiteral internal constructor(
        override val start: Int,
    ) : LiteralExpression(null, "null") {
        override val end = start + 4
    }

    data class BooleanLiteral internal constructor(
        override val start: Int,
        override val value: Boolean
    ) : LiteralExpression(value, "boolean") {
        override val end = start + if (value) 4 else 5
    }

    data class IntLiteral internal constructor(
        override val start: Int,
        override val end: Int,
        override val value: Int
    ) : LiteralExpression(value, "int")

    data class StringLiteral internal constructor(
        override val start: Int,
        override val end: Int,
        override val value: String,
        internal val matches: ((CharSequence) -> Boolean)? = null
    ) : LiteralExpression(value, "string") {

        override fun stringify() = escapeString(value)

        internal val outMatches = matches?.let { optimizeMatchString(value) ?: it } ?: { false }
    }
}
