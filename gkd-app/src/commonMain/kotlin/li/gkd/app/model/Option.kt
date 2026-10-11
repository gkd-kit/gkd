package li.gkd.app.model

import org.jetbrains.compose.resources.StringResource

interface Option<T> {
    val value: T
    val labelResource: StringResource
    val options: List<Option<T>>
}

fun <V, T : Option<V>> Iterable<T>.findOption(value: V): T =
    find { it.value == value } ?: first()
