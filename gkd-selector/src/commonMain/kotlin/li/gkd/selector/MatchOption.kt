package li.gkd.selector

import kotlin.js.JsExport

@JsExport
data class MatchOption(
    val fastQuery: Boolean = false,
) {
    companion object {
        val default = MatchOption()
    }
}
