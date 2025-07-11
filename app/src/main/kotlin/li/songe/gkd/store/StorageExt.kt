package li.songe.gkd.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.appScope
import li.songe.gkd.util.json
import li.songe.gkd.util.privateStoreFolder
import li.songe.gkd.util.storeFolder
import java.io.File

private fun getStoreFile(name: String, private: Boolean): File {
    return (if (private) privateStoreFolder else storeFolder).resolve(name)
}

private fun readStoreText(
    name: String,
    private: Boolean,
): String? = getStoreFile(name, private).run {
    if (exists()) {
        readText()
    } else {
        null
    }
}

private fun writeStoreText(name: String, text: String, private: Boolean) {
    getStoreFile(name, private).writeText(text)
}

fun <T> createTextFlow(
    key: String,
    decode: (String?) -> T,
    encode: (T) -> String,
    private: Boolean = false,
    scope: CoroutineScope = appScope,
): MutableStateFlow<T> {
    val name = if (key.contains('.')) key else "$key.txt"
    val initText = readStoreText(name, private)
    val initValue = decode(initText)
    val stateFlow = MutableStateFlow(initValue)
    scope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) {
                writeStoreText(name, encode(it), private)
            }
        }
    }
    return stateFlow
}

inline fun <reified T> createAnyFlow(
    key: String,
    crossinline default: () -> T,
    crossinline initialize: (T) -> T = { it },
    private: Boolean = false,
    scope: CoroutineScope = appScope,
): MutableStateFlow<T> {
    return createTextFlow(
        key = "$key.json",
        decode = {
            val initValue = it?.let {
                runCatching { json.decodeFromString<T>(it) }.getOrNull()
            }
            initialize(initValue ?: default())
        },
        encode = {
            json.encodeToString(it)
        },
        private = private,
        scope = scope,
    )
}
