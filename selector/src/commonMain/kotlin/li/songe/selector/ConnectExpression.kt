package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed class ConnectExpression : Stringify {
    abstract val minOffset: Int
    abstract val maxOffset: Int?
    abstract fun checkOffset(offset: Int): Boolean
    abstract fun getOffset(i: Int): Int
}
