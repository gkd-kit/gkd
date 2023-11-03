package li.songe.selector

internal interface NodeMatchFc {
    operator fun <T> invoke(node: T, transform: Transform<T>): Boolean
}

interface NodeSequenceFc {
    operator fun <T> invoke(sq: Sequence<T?>): Sequence<T?>
}

internal val emptyNodeSequence = object : NodeSequenceFc {
    override fun <T> invoke(sq: Sequence<T?>) = emptySequence<T?>()
}

internal interface NodeTraversalFc {
    operator fun <T> invoke(node: T, transform: Transform<T>): Sequence<T?>
}
