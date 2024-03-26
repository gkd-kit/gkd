package li.songe.matches

import kotlin.js.JsExport

// wasm gc
@JsExport
fun toMatches(source: String): (input: String) -> Boolean {
    val regex = Regex(source)
    return { input -> regex.matches(input) }
}