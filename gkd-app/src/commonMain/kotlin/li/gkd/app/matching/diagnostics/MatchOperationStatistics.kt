package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class MatchOperationTotal(
    val name: String,
    val rule: String?,
    val count: Long,
    val totalDurationNanos: Long,
    val slowest: MatchOperation,
)

@Serializable
data class MatchOperationStatistics(
    val completed: Long,
    val omitted: Long,
    val totals: List<MatchOperationTotal>,
)

/** Per-session totals outlive the detail rings. Called under the diagnostics lock. */
class MatchOperationStatisticsRecorder {
    private class Total(first: MatchOperation) {
        var count = 0L
        var nanos = 0L
        var slowest = first.copy(recording = null, arguments = emptyList())
    }

    private val totals = linkedMapOf<Pair<String, String?>, Total>()
    private var completed = 0L
    private var omitted = 0L

    fun record(operation: MatchOperation) {
        val duration = checkNotNull(operation.durationNanos)
        completed++
        val rule =
            operation.rule.takeIf { operation.name == "Query" || operation.name == "SelectorQuery" }
        val key = operation.name to rule
        val total =
            totals[key]
                ?: run {
                    if (totals.size == 128) {
                        omitted++
                        return
                    }
                    Total(operation).also { totals[key] = it }
                }
        total.count++
        total.nanos += duration
        if (duration > checkNotNull(total.slowest.durationNanos)) {
            total.slowest = operation.copy(recording = null, arguments = emptyList())
        }
    }

    fun snapshot() =
        MatchOperationStatistics(
            completed,
            omitted,
            totals.map { (key, total) ->
                MatchOperationTotal(key.first, key.second, total.count, total.nanos, total.slowest)
            },
        )
}
