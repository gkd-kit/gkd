package li.songe.selector.data

import li.songe.selector.Transform

sealed class Expression {
    abstract fun <T> match(node: T, transform: Transform<T>): Boolean

    abstract val propertyNames: List<String>
}
