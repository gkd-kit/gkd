package li.songe.selector.data

import li.songe.selector.NodeSequenceFc

sealed class ConnectExpression {
    abstract val isConstant: Boolean
    abstract val minOffset: Int

    internal abstract val traversal: NodeSequenceFc
}
