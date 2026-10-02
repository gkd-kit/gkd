package li.gkd.app.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.app.storage.appStorage

/** Explicit saves publish only after persistence succeeds, on both hosts. */
object GithubCookieStore {
    private val files by lazy { FileSettingsStorage(appStorage().privateStore) }
    private val mutex = Mutex()
    val value: StateFlow<String>
        field = MutableStateFlow(files.read("github_cookie.txt").orEmpty())

    suspend fun save(text: String) = mutex.withLock {
        val next = text.filter { it != '\n' && it != '\r' }.trim()
        withContext(Dispatchers.IO) { files.writeAtomically("github_cookie.txt", next) }
        value.value = next
    }
}
