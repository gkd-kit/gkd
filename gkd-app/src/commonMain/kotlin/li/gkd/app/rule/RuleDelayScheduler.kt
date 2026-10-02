package li.gkd.app.rule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Jobs belong to the service scope, while resets cancel both kinds of pending work. */
class RuleDelayScheduler {
    enum class Kind { Match, Action }
    private data class Key(val rule: ResolvedRule, val kind: Kind)

    private val jobs = mutableMapOf<Key, Job>()

    fun schedule(
        rule: ResolvedRule, kind: Kind, scope: CoroutineScope,
        context: CoroutineContext, delayMillis: Long, resume: () -> Unit,
    ) {
        val key = Key(rule, kind)
        val job = synchronized(jobs) {
            if (jobs.containsKey(key)) return
            scope.launch(context, start = CoroutineStart.LAZY) {
                delay(delayMillis)
                // Clear before resuming, as the resumed query may schedule another delay.
                val currentJob = currentCoroutineContext()[Job]
                val pending = synchronized(jobs) {
                    if (jobs[key] === currentJob) {
                        jobs.remove(key); true
                    } else false
                }
                if (pending) resume()
            }.also { job ->
                jobs[key] = job
                job.invokeOnCompletion {
                    synchronized(jobs) { if (jobs[key] === job) jobs.remove(key) }
                }
            }
        }
        job.start()
    }

    fun cancel(rule: ResolvedRule) {
        synchronized(jobs) {
            Kind.entries.forEach { kind -> jobs.remove(Key(rule, kind))?.cancel() }
        }
    }
}
