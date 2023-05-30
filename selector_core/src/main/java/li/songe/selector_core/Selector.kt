package li.songe.selector_core

import li.songe.selector_core.data.PropertyWrapper
import li.songe.selector_core.parser.ParserSet

data class Selector(private val propertyWrapper: PropertyWrapper) {
    override fun toString() = propertyWrapper.toString()
//    val segments by lazy {
//        sequence {
//            var c = propertyWrapper.to
//            yield(propertyWrapper.propertySegment)
//            while (c != null) {
//                yield(c!!.connectSegment)
//                yield(c!!.to.propertySegment)
//                c = c!!.to.to
//            }
//        }.toList().reversed()
//    }

    fun match(node: NodeExt): NodeExt? {
        val trackNodes = propertyWrapper.match(node) ?: return null
        return trackNodes.lastOrNull() ?: node
    }

    companion object {
        fun parse(source: String) = ParserSet.selectorParser(source)
    }
}