package li.songe.selector

import kotlin.js.JsExport

expect fun String.toMatches(): (input: CharSequence) -> Boolean

expect fun setWasmToMatches(wasmToMatches: (String) -> (String) -> Boolean)

@JsExport
fun updateWasmToMatches(toMatches: (String) -> (String) -> Boolean) {
    setWasmToMatches(toMatches)
}