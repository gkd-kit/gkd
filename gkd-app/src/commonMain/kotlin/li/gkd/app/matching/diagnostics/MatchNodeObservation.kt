package li.gkd.app.matching.diagnostics

/** Selector access is synchronous. The reader explicitly carries this context to its worker. */
class MatchNodeObservation(
    val operation: MatchOperation?,
    val sessionId: String,
    val value: (Any?) -> MatchRecordedValue,
    val recording: MatchQueryEvidence?,
) {
    companion object {
        private val local = ThreadLocal<MatchNodeObservation>()

        fun current(): MatchNodeObservation? = local.get()

        fun <T> observe(
            operation: MatchOperation?,
            value: (Any?) -> MatchRecordedValue,
            block: () -> T,
            sessionId: String? = operation?.sessionId,
            recording: MatchQueryEvidence? = null,
        ): T {
            if (sessionId == null) return block()
            val previous = local.get()
            local.set(
                MatchNodeObservation(operation, sessionId, value, recording ?: previous?.recording),
            )
            try {
                return block()
            } finally {
                if (previous == null) local.remove() else local.set(previous)
            }
        }
    }
}
