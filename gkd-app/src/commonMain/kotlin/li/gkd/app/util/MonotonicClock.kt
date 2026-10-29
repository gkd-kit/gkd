package li.gkd.app.util

/** Process-local elapsed time. Never persist this value as a calendar timestamp. */
fun monotonicTimeMillis(): Long = System.nanoTime() / 1_000_000
