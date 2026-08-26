package li.songe.selector.property

import li.songe.selector.QueryContext
import li.songe.selector.Stringify
import li.songe.selector.Transform
import li.songe.selector.comparePrimitiveValue
import li.songe.selector.escapeString
import li.songe.selector.optimizeMatchString
import li.songe.selector.whenNull
import kotlin.js.JsExport

@JsExport
sealed class ValueExpression(open val value: Any?, open val type: String) : Stringify {
    override fun stringify() = value.toString()
    internal abstract fun <T> getAttr(
        context: QueryContext<T>,
        transform: Transform<T>,
    ): Any?

    abstract val properties: Array<String>
    abstract val methods: Array<String>

    sealed class Variable(
        override val value: String,
    ) : ValueExpression(value, "var")

    data class Identifier(
        val name: String,
    ) : Variable(name) {
        override fun <T> getAttr(context: QueryContext<T>, transform: Transform<T>): Any? {
            return transform.getAttr(context, value)
        }

        override val properties: Array<String>
            get() = arrayOf(value)
        override val methods: Array<String>
            get() = emptyArray()

        val isEqual = name == "equal"
        val isNotEqual = name == "notEqual"
    }

    data class MemberExpression(
        val object0: Variable,
        val property: String,
    ) : Variable(value = "${object0.stringify()}.$property") {
        override fun <T> getAttr(
            context: QueryContext<T>,
            transform: Transform<T>,
        ): Any? {
            return transform.getAttr(
                object0.getAttr(context, transform).whenNull { return null },
                property
            )
        }

        override val properties: Array<String>
            get() = arrayOf(*object0.properties, property)
        override val methods: Array<String>
            get() = object0.methods

        val isPropertyOr = property == "or"
        val isPropertyAnd = property == "and"
        val isPropertyIfElse = property == "ifElse"
    }

    data class CallExpression(
        val callee: Variable,
        val arguments: List<ValueExpression>,
    ) : Variable(
        value = "${callee.stringify()}(${arguments.joinToString(",") { it.stringify() }})",
    ) {

        override fun <T> getAttr(
            context: QueryContext<T>,
            transform: Transform<T>,
        ): Any? {
            return when (callee) {
                is CallExpression -> {
                    // not support
                    null
                }

                is Identifier -> {
                    when {
                        callee.isEqual -> {
                            comparePrimitiveValue(
                                arguments[0].getAttr(context, transform),
                                arguments[1].getAttr(context, transform)
                            )
                        }

                        callee.isNotEqual -> {
                            !comparePrimitiveValue(
                                arguments[0].getAttr(context, transform),
                                arguments[1].getAttr(context, transform)
                            )
                        }

                        else -> {
                            transform.getInvoke(
                                context,
                                callee.name,
                                arguments.map {
                                    it.getAttr(context, transform).whenNull { return null }
                                }
                            )
                        }
                    }
                }

                is MemberExpression -> {
                    val objectValue =
                        callee.object0.getAttr(context, transform).whenNull { return null }
                    when {
                        callee.isPropertyOr -> {
                            (objectValue as Boolean) ||
                                    (arguments[0].getAttr(context, transform)
                                        .whenNull { return null } as Boolean)
                        }

                        callee.isPropertyAnd -> {
                            (objectValue as Boolean) &&
                                    (arguments[0].getAttr(context, transform)
                                        .whenNull { return null } as Boolean)
                        }

                        callee.isPropertyIfElse -> {
                            if (objectValue as Boolean) {
                                arguments[0].getAttr(context, transform)
                            } else {
                                arguments[1].getAttr(context, transform)
                            }
                        }

                        else -> transform.getInvoke(
                            objectValue,
                            callee.property,
                            arguments.map {
                                it.getAttr(context, transform).whenNull { return null }
                            }
                        )
                    }

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
                is Identifier -> arrayOf(callee.name)
                is MemberExpression -> arrayOf(*callee.object0.methods, callee.property)
            }.toMutableList().plus(arguments.flatMap { it.methods.toList() })
                .toTypedArray()
    }

    sealed class LiteralExpression(
        override val value: Any?,
        override val type: String,
    ) : ValueExpression(value, type) {
        override fun <T> getAttr(
            context: QueryContext<T>,
            transform: Transform<T>,
        ) = value

        override val properties: Array<String>
            get() = emptyArray()
        override val methods: Array<String>
            get() = emptyArray()
    }

    data object NullLiteral : LiteralExpression(null, "null")

    data class BooleanLiteral(override val value: Boolean) : LiteralExpression(value, "boolean")

    data class IntLiteral(override val value: Int) : LiteralExpression(value, "int")

    @ConsistentCopyVisibility
    data class StringLiteral internal constructor(
        override val value: String,
        internal val matches: ((CharSequence) -> Boolean)? = null
    ) : LiteralExpression(value, "string") {

        override fun stringify() = escapeString(value)

        internal val outMatches = matches?.let { optimizeMatchString(value) ?: it } ?: { false }
    }
}
