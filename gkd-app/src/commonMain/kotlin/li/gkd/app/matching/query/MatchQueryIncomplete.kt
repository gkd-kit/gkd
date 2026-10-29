package li.gkd.app.matching.query

/** An unknown query result must never satisfy an exclusion selector. */
class MatchQueryIncomplete(val reason: MatchQueryIncompleteReason) :
    RuntimeException(reason.wireValue)

/** Short-circuit matches remain valid; exhaustion beyond the budget is not a negative result. */
fun <T> Sequence<T>.withQueryLimit(limit: Int, reason: MatchQueryIncompleteReason): Sequence<T> =
    sequence {
        var count = 0
        for (value in this@withQueryLimit) {
            if (count++ == limit) throw MatchQueryIncomplete(reason)
            yield(value)
        }
    }
