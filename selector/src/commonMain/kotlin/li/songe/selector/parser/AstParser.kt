package li.songe.selector.parser

import li.songe.selector.Selector
import li.songe.selector.property.Expression
import kotlin.js.JsExport


@JsExport
data class AstNode<T>(
    val start: Int,
    val end: Int,
    val value: T,
    val children: List<AstNode<*>>,
)

private data class AstContext(
    val parent: AstContext? = null,
    val children: MutableList<AstNode<*>> = mutableListOf()
)

internal class AstParser(override val source: CharSequence) : SelectorParser(source) {
    private var tempAstContext = AstContext()

    private fun <T> createAstNode(block: () -> T): T {
        tempAstContext = AstContext(tempAstContext)
        val start = i
        return block().apply {
            val end = i
            val value = this
            tempAstContext.parent?.let {
                it.children.add(AstNode(start, end, value, tempAstContext.children))
                tempAstContext = it
            }
        }
    }

    fun readAst(): AstNode<Selector> {
        readSelector()
        @Suppress("UNCHECKED_CAST")
        return tempAstContext.children.single() as AstNode<Selector>
    }

    override fun readSelector(): Selector {
        return createAstNode { super.readSelector() }
    }

    override fun readExpression(): Expression {
        return createAstNode { super.readExpression() }
    }
}