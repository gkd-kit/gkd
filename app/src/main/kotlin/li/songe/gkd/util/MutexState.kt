package li.songe.gkd.util

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MutexState() {
    val mutex: Mutex = Mutex()
    val intState = MutableStateFlow(0)

    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    val state = object : StateFlow<Boolean> {
        override val value: Boolean
            get() = intState.value > 0
        override val replayCache: List<Boolean>
            get() = listOf(value)

        override suspend fun collect(collector: FlowCollector<Boolean>): Nothing {
            var currentValue = value
            collector.emit(currentValue)
            intState.collect {
                val newValue = it > 0
                if (newValue != currentValue) {
                    currentValue = newValue
                    collector.emit(currentValue)
                }
            }
        }
    }

    suspend inline fun withStateLock(block: () -> Unit): Unit = mutex.withLock {
        intState.update { it + 1 }
        try {
            block()
        } finally {
            intState.update { it - 1 }
        }
    }

    suspend inline fun whenUnLock(block: () -> Unit) {
        if (mutex.isLocked) return
        withStateLock(block)
    }
}
