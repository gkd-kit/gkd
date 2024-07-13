package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class MatchOption(
    val quickFind: Boolean = false,
    val fastQuery: Boolean = false,
)

val defaultMatchOption = MatchOption()
