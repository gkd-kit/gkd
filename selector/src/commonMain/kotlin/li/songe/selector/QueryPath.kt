package li.songe.selector

import li.songe.selector.connect.ConnectWrapper
import li.songe.selector.property.PropertyWrapper
import kotlin.js.JsExport

@Suppress("unused")
@JsExport
data class QueryPath<T>(
    val propertyWrapper: PropertyWrapper,
    val connectWrapper: ConnectWrapper,
    val offset: Int,
    val source: T,
    val target: T,
) {
    val formatConnectOffset: String
        get() = connectWrapper.segment.operator.formatOffset(offset)
}