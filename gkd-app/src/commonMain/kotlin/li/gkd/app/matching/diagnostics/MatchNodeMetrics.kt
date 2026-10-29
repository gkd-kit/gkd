package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable

const val NODE_READ_OPERATION_NAME = "NodeRead"

@Serializable
enum class NodeReadOperation {
    Root,
    Child,
    Parent,
    FindText,
    FindId,
    Refresh,
}

@Serializable
enum class NodeCacheKind {
    Root,
    Child,
    Parent,
    Index,
}

@Serializable
data class NodeReadMetric(val operation: NodeReadOperation, val calls: Long, val nanos: Long)

@Serializable data class NodeCacheMetric(val kind: NodeCacheKind, val hits: Long, val misses: Long)

@Serializable
data class NodeMetricSnapshot(
    val reads: List<NodeReadMetric>,
    val caches: List<NodeCacheMetric>,
    val attributes: Long,
) {
    operator fun minus(before: NodeMetricSnapshot) =
        NodeMetricSnapshot(
            reads.zip(before.reads) { a, b ->
                a.copy(calls = a.calls - b.calls, nanos = a.nanos - b.nanos)
            },
            caches.zip(before.caches) { a, b ->
                a.copy(hits = a.hits - b.hits, misses = a.misses - b.misses)
            },
            attributes - before.attributes,
        )
}

/** Owned by a single benchmark worker. Ordinary matching never creates this collector. */
class MatchNodeMetrics {
    private val calls = LongArray(NodeReadOperation.entries.size)
    private val nanos = LongArray(NodeReadOperation.entries.size)
    private val hits = LongArray(NodeCacheKind.entries.size)
    private val misses = LongArray(NodeCacheKind.entries.size)
    private var attributes = 0L

    fun recordRead(operation: NodeReadOperation, elapsedNanos: Long) {
        calls[operation.ordinal]++
        nanos[operation.ordinal] += elapsedNanos
    }

    inline fun <T> measure(operation: NodeReadOperation, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            recordRead(operation, System.nanoTime() - start)
        }
    }

    fun cache(kind: NodeCacheKind, hit: Boolean) {
        if (hit) hits[kind.ordinal]++ else misses[kind.ordinal]++
    }

    fun attribute() {
        attributes++
    }

    fun snapshot() =
        NodeMetricSnapshot(
            NodeReadOperation.entries.map {
                NodeReadMetric(it, calls[it.ordinal], nanos[it.ordinal])
            },
            NodeCacheKind.entries.map { NodeCacheMetric(it, hits[it.ordinal], misses[it.ordinal]) },
            attributes,
        )
}
