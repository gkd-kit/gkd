package li.gkd.app.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex

class MutexState() {
    private val mutex = Mutex()

    val state: StateFlow<Boolean>
        field: MutableStateFlow<Boolean> = MutableStateFlow(false)

    suspend fun <T> withStateLock(block: suspend () -> T): T {
        mutex.lock()
        state.value = true
        try {
            return block()
        } finally {
            state.value = false
            mutex.unlock()
        }
    }

    suspend fun tryWithStateLock(block: suspend () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        state.value = true
        try {
            block()
            return true
        } finally {
            state.value = false
            mutex.unlock()
        }
    }
}
