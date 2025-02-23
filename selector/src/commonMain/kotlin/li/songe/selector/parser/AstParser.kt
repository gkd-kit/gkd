package li.songe.selector.parser

import li.songe.selector.Selector
import li.songe.selector.property.LogicalExpression
import li.songe.selector.property.ValueExpression
import li.songe.selector.unit.LogicalSelectorExpression

private data class AstContext(
    val parent: AstContext? = null,
    val children: MutableList<AstNode<*>> = mutableListOf()
)

internal class AstParser(override val source: String) : SelectorParser(source) {
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

    private fun traverse(block: (node: AstNode<*>) -> Unit) {
        val stack = mutableListOf(tempAstContext.children.single())
        do {
            val top = stack.removeLast()
            block(top)
            stack.addAll(top.children.asReversed())
        } while (stack.isNotEmpty())
    }

    private fun prueAst(): Boolean {
        var changed = false
        traverse { node ->
            val children = node.children as MutableList
            if (children.size == 1) {
                val child = children.first()
                if (child.children.isEmpty() && child.start == node.start && child.end == node.end) {
                    if (child.value is String || child.value is Boolean || child.value is Int || child.value == null) {
                        children.clear()
                        changed = true
                    }
                }
            }
        }
        traverse { node ->
            val children = node.children as MutableList
            children.forEachIndexed { i, child ->
                if (child.children.size == 1) {
                    val deepChild = child.children.first()
                    val isSameType =
                        child.value === deepChild.value || child.value::class == deepChild.value::class
                    if (isSameType && child.sameRange(deepChild)) {
                        children[i] = deepChild
                        changed = true
                    }
                }
            }
        }
        traverse { node ->
            val children = node.children as MutableList
            children.forEachIndexed { i, child ->
                if (child.children.size == 1) {
                    val deepChild = child.children.first()
                    val isSameType =
                        child.value === deepChild.value || child.value::class == deepChild.value::class
                    if (isSameType && source[child.start] == '(' && source[child.end - 1] == ')') {
                        children[i] = AstNode(
                            start = child.start,
                            end = child.end,
                            value = child.value,
                            children = deepChild.children
                        )
                        changed = true
                    }
                }
            }
        }
        traverse { node ->
            val children = node.children as MutableList
            children.forEachIndexed { i, child ->
                if (child.children.isNotEmpty() && source[child.start] == '(' && source[child.end - 1] == ')' && child.children.first().start > child.start && child.children.last().end < child.end) {
                    children[i] = AstNode(
                        start = child.children.first().start,
                        end = child.children.last().end,
                        value = child.value,
                        children = child.children
                    )
                    changed = true
                }
            }
        }
        return changed
    }

    fun readAst(): AstNode<Selector> {
        readSelector()
        while (true) {
            if (!prueAst()) {
                break
            }
        }
        @Suppress("UNCHECKED_CAST")
        return tempAstContext.children.single() as AstNode<Selector>
    }

    override fun readLiteral(v: String) = super.readLiteral(v)
    override fun readSelectorExpression() = super.readSelectorExpression()

    fun mergeCommonLogicalExpression(expression: Any, leftIndex: Int) {
        val children = tempAstContext.children.subList(leftIndex, leftIndex + 3).toMutableList()
        tempAstContext.children[leftIndex] = AstNode(
            start = children.first().start,
            end = children.last().end,
            value = expression,
            children = children
        )
        repeat(children.size - 1) {
            tempAstContext.children.removeAt(leftIndex + 1)
        }
    }

    override fun mergeLogicalExpression(expression: LogicalExpression) {
        val leftIndex = tempAstContext.children.indexOfFirst { it.value === expression.left }
        mergeCommonLogicalExpression(expression, leftIndex)
    }

    override fun mergeLogicalSelectorExpression(expression: LogicalSelectorExpression) {
        val leftIndex = tempAstContext.children.indexOfFirst { it.value === expression.left }
        mergeCommonLogicalExpression(expression, leftIndex)
    }

    override fun mergeIdentifier(expression: ValueExpression.Identifier) {
        val lastNode = tempAstContext.children.last()
        if (lastNode.value is String) {
            tempAstContext.children[tempAstContext.children.size - 1] = AstNode(
                start = lastNode.start,
                end = lastNode.end,
                children = lastNode.children,
                value = expression,
            )
        }
    }

    override fun mergeMemberExpression(expression: ValueExpression.MemberExpression) {
        val children = tempAstContext.children.subList(
            tempAstContext.children.size - 2,
            tempAstContext.children.size
        ).toMutableList()
        repeat(children.size) {
            tempAstContext.children.removeLast()
        }
        tempAstContext.children.add(
            AstNode(
                start = children.first().start,
                end = children.last().end,
                children = children,
                value = expression,
            )
        )
    }

    override fun mergeCallExpression(expression: ValueExpression.CallExpression) {
        if (source[i - 1] != ')') {
            errorExpect("CallExpression End")
        }
        val children = tempAstContext.children.subList(
            tempAstContext.children.size - expression.arguments.size - 1,
            tempAstContext.children.size
        ).toMutableList()
        repeat(children.size) {
            tempAstContext.children.removeLast()
        }
        tempAstContext.children.add(
            AstNode(
                start = children.first().start,
                end = i,
                children = children,
                value = expression,
            )
        )
    }

    override fun <T> readBracketExpression(block: () -> T) = createAstNode {
        super.readBracketExpression(block)
    }

    override fun readVariableName() = createAstNode { super.readVariableName() }

    override fun readBinaryExpression() = createAstNode { super.readBinaryExpression() }

    override fun readSelector() = createAstNode { super.readSelector() }

    override fun readExpression() = createAstNode { super.readExpression() }

    override fun readCompareOperator() = createAstNode { super.readCompareOperator() }

    override fun readInt() = createAstNode { super.readInt() }

    override fun readUInt() = createAstNode { super.readUInt() }

    override fun readLogicalOperator() = createAstNode { super.readLogicalOperator() }

    override fun readNotExpression() = createAstNode { super.readNotExpression() }

    override fun readNotSelectorExpression() = createAstNode { super.readNotSelectorExpression() }

    override fun readPropertyName() = createAstNode { super.readPropertyName() }

    override fun readPropertySegment() = createAstNode { super.readPropertySegment() }

    override fun readPropertyUnit() = createAstNode { super.readPropertyUnit() }

    override fun readSelectorLogicalOperator() = createAstNode {
        super.readSelectorLogicalOperator()
    }

    override fun readString() = createAstNode { super.readString() }

    override fun readUnitSelectorExpression() = createAstNode {
        super.readUnitSelectorExpression()
    }

    override fun readValueExpression() = createAstNode { super.readValueExpression() }

    override fun readConnectExpression() = createAstNode { super.readConnectExpression() }

    override fun readConnectOperator() = createAstNode { super.readConnectOperator() }

    override fun readConnectSegment() = createAstNode { super.readConnectSegment() }

    override fun readMonomial() = createAstNode { super.readMonomial() }

    override fun readPolynomialExpression() = createAstNode { super.readPolynomialExpression() }

    override fun readTupleExpression() = createAstNode { super.readTupleExpression() }

}
