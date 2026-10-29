package li.gkd.app.matching.diagnostics

/** Thread-safe value snapshots only; export never waits for or invokes a platform read. */
class MatchQueryEvidence(initial: MatchQueryRecording? = null) {
    private val lock = Any()
    private var header = initial
    private val calls = initial?.calls?.toMutableList() ?: mutableListOf()
    private var characters = 0
    private val operations = initial?.operations?.toMutableList() ?: mutableListOf()
    @Volatile
    var incomplete: String? = initial?.incomplete
        private set

    val completed: Boolean
        get() = synchronized(lock) { header?.completed == true }

    fun begin(value: MatchQueryRecording) =
        synchronized(lock) {
            header = value
        }

    fun truncate(reason: String) = synchronized(lock) { incomplete = reason }

    fun append(call: MatchNodeCall) =
        synchronized(lock) {
            if (incomplete != null) return@synchronized
            characters +=
                call.operation.wireValue.length +
                    call.arguments.sumOf { it.value.length + 32 } +
                    (call.result?.value?.length ?: 0) +
                    64
            if (calls.size >= 10_000 || characters > 1_000_000) {
                incomplete = "recording_limit"
                return@synchronized
            }
            calls.add(call)
        }

    fun operation(value: MatchOperation) =
        synchronized(lock) {
            characters +=
                value.name.length +
                    value.arguments.sumOf { it.value.length + 32 } +
                    (value.outcome?.length ?: 0) +
                    64
            if (operations.size < 10_000 && characters <= 1_000_000)
                operations.add(value.copy(recording = null))
            else incomplete = "recording_limit"
        }

    fun finish(
        result: MatchRecordedValue,
        failure: String?,
        endedAt: Long? = null,
        durationNanos: Long? = null,
        outcome: String? = null,
    ) =
        synchronized(lock) {
            header =
                checkNotNull(header)
                    .copy(
                        result = result,
                        failure = failure,
                        completed = true,
                        endedAt = endedAt,
                        durationNanos = durationNanos,
                        outcome = outcome,
                    )
            if (incomplete == null && failure != null && calls.lastOrNull()?.failure != failure) {
                incomplete = "failure_outside_node_access"
            }
        }

    fun snapshot(): MatchQueryRecording =
        synchronized(lock) {
            checkNotNull(header)
                .copy(
                    calls = calls.toList(),
                    operations = operations.toList(),
                    incomplete = incomplete,
                )
        }
}
