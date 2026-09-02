package li.gkd.selector

import kotlin.js.JsExport

@JsExport
public data class MatchOptions(
    val fastQuery: Boolean = false,
) {
    public companion object {
        public val default: MatchOptions = MatchOptions()
    }
}
