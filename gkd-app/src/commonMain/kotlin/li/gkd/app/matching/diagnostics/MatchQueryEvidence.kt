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

    val detailsRetained: Boolean
        get() = synchronized(lock) { header?.detailsRetained != false }

    val retainedFor: String?
        get() = synchronized(lock) { header?.retainedFor }

    fun identify(id: Long) = synchronized(lock) { header = checkNotNull(header).copy(queryId = id) }

    fun protect(reason: String?) =
        synchronized(lock) {
            header = checkNotNull(header).copy(retainedFor = reason)
        }

    fun releaseDetails(): Boolean =
        synchronized(lock) {
            if (header?.detailsRetained != true || header?.completed != true)
                return@synchronized false
            calls.clear()
            operations.clear()
            header =
                checkNotNull(header)
                    .copy(calls = emptyList(), operations = emptyList(), detailsRetained = false)
            true
        }

    fun begin(value: MatchQueryRecording) =
        synchronized(lock) {
            header = value
        }

    fun truncate(reason: String) = synchronized(lock) { incomplete = reason }

    fun append(call: MatchNodeCall) =
        synchronized(lock) {
            if (incomplete != null || header?.detailsRetained == false) return@synchronized
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
            if (header?.detailsRetained == false) return@synchronized
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

    fun snapshot(includeDetails: Boolean = true): MatchQueryRecording =
        synchronized(lock) {
            checkNotNull(header)
                .copy(
                    calls = if (includeDetails) calls.toList() else emptyList(),
                    operations = if (includeDetails) operations.toList() else emptyList(),
                    incomplete = incomplete,
                )
        }
}
