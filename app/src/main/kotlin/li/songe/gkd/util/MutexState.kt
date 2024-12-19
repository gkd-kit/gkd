package li.songe.gkd.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MutexState {
    val mutex = Mutex()
    val state = MutableStateFlow(false)
    suspend inline fun withLock(block: () -> Unit): Unit = mutex.withLock {
        state.value = true
        try {
            block()
        } finally {
            state.value = false
        }
    }
}
