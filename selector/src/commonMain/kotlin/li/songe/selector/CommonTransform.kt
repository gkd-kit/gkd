package li.songe.selector

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class CommonTransform<T : Any>(
    getAttr: (T, String) -> Any?,
    getName: (T) -> String?,
    getChildren: (T) -> Array<T>,
    getParent: (T) -> T?,
) {
    internal val transform = Transform(
        getAttr = getAttr,
        getName = getName,
        getChildren = { node -> getChildren(node).asSequence() },
        getParent = getParent,
    )

    @Suppress("UNCHECKED_CAST", "UNUSED")
    val querySelectorAll: (T, CommonSelector) -> Array<T> = { node, selector ->
        val result =
            transform.querySelectorAll(node, selector.selector).toList().toTypedArray<Any?>()
        result as Array<T>
    }

    @Suppress("UNUSED")
    val querySelector: (T, CommonSelector) -> T? = { node, selector ->
        transform.querySelectorAll(node, selector.selector).firstOrNull()
    }


    @Suppress("UNCHECKED_CAST", "UNUSED")
    val querySelectorTrackAll: (T, CommonSelector) -> Array<Array<T>> = { node, selector ->
        val result = transform.querySelectorTrackAll(node, selector.selector)
            .map { it.toTypedArray<Any?>() as Array<T> }.toList().toTypedArray<Any?>()
        result as Array<Array<T>>
    }

    @Suppress("UNCHECKED_CAST", "UNUSED")
    val querySelectorTrack: (T, CommonSelector) -> Array<T>? = { node, selector ->
        transform.querySelectorTrackAll(node, selector.selector).firstOrNull()
            ?.toTypedArray<Any?>() as Array<T>?
    }

}