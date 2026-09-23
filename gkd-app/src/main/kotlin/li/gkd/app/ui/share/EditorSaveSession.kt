package li.gkd.app.ui.share

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A completed editor commits once; failed or cancelled saves may be retried. */
class EditorSaveSession<T> {
    private data class Saved<T>(val value: T)

    private val mutex = Mutex()
    private var saved: Saved<T>? = null

    suspend fun save(action: suspend () -> T): T = mutex.withLock {
        saved?.let { return@withLock it.value }
        action().also { saved = Saved(it) }
    }
}
