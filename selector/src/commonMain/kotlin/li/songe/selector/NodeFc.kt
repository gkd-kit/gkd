package li.songe.selector

internal interface NodeMatchFc {
    operator fun <T> invoke(node: T, transform: Transform<T>): Boolean
}

internal interface NodeSequenceFc {
    operator fun <T> invoke(sequence: Sequence<T?>): Sequence<T?>
}

internal interface NodeTraversalFc {
    operator fun <T> invoke(node: T, transform: Transform<T>): Sequence<T?>
}

