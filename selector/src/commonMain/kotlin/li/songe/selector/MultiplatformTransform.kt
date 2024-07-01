package li.songe.selector

import kotlin.js.JsExport

@JsExport
@Suppress("UNCHECKED_CAST", "UNUSED")
class MultiplatformTransform<T : Any>(
    getAttr: (Any?, String) -> Any?,
    getInvoke: (Any?, String, List<Any?>) -> Any?,
    getName: (T) -> String?,
    getChildren: (T) -> Array<T>,
    getParent: (T) -> T?,
) {
    internal val transform = Transform(
        getAttr = getAttr,
        getInvoke = getInvoke,
        getName = getName,
        getChildren = { node -> getChildren(node).asSequence() },
        getParent = getParent,
    )

    val querySelectorAll: (T, MultiplatformSelector) -> Array<T> = { node, selector ->
        val result =
            transform.querySelectorAll(node, selector.selector).toList().toTypedArray<Any?>()
        result as Array<T>
    }

    val querySelector: (T, MultiplatformSelector) -> T? = { node, selector ->
        transform.querySelectorAll(node, selector.selector).firstOrNull()
    }

    val querySelectorTrackAll: (T, MultiplatformSelector) -> Array<Array<T>> = { node, selector ->
        val result = transform.querySelectorTrackAll(node, selector.selector)
            .map { it.toTypedArray<Any?>() as Array<T> }.toList().toTypedArray<Any?>()
        result as Array<Array<T>>
    }

    val querySelectorTrack: (T, MultiplatformSelector) -> Array<T>? = { node, selector ->
        transform.querySelectorTrackAll(node, selector.selector).firstOrNull()
            ?.toTypedArray<Any?>() as Array<T>?
    }

}