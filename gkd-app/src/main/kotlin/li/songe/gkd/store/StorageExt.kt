package li.songe.gkd.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.appScope
import li.songe.gkd.util.json
import li.songe.gkd.util.privateStoreFolder
import li.songe.gkd.util.storeFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption


private fun readStoreText(
    file: File
): String? = file.run {
    if (exists()) {
        readText()
    } else {
        null
    }
}

private fun writeStoreText(file: File, text: String) {
    val tempFile = File("${file.absolutePath}.tmp")
    tempFile.outputStream().use {
        it.write(text.toByteArray(Charsets.UTF_8))
        it.fd.sync()
    }
    Files.move(
        tempFile.toPath(),
        file.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
    )
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class MutableStoreStateFlow<T>(
    val filename: String,
    val decode: (String?) -> T,
    val encode: (T) -> String,
    private val stateFlow: MutableStateFlow<T>,
) : MutableStateFlow<T> by stateFlow {
    fun encodeSelf(): String = encode(value)
    fun updateByDecode(text: String?) {
        value = decode(text)
    }
}

fun <T> createTextFlow(
    key: String,
    decode: (String?) -> T,
    encode: (T) -> String,
    private: Boolean = false,
    scope: CoroutineScope = appScope,
    debounceMillis: Long = 0,
): MutableStoreStateFlow<T> {
    val filename = if (key.contains('.')) key else "$key.txt"
    val file = (if (private) privateStoreFolder else storeFolder).resolve(filename)
    val initText = readStoreText(file)
    val initValue = decode(initText)
    val stateFlow = MutableStateFlow(initValue)
    scope.launch {
        stateFlow.drop(1).conflate().debounce(debounceMillis).collect {
            withContext(Dispatchers.IO) {
                writeStoreText(file, encode(it))
            }
        }
    }
    return MutableStoreStateFlow(
        filename = filename,
        decode = decode,
        encode = encode,
        stateFlow = stateFlow,
    )
}

inline fun <reified T> createAnyFlow(
    key: String,
    crossinline default: () -> T,
    crossinline initialize: (T) -> T = { it },
    private: Boolean = false,
    scope: CoroutineScope = appScope,
    debounceMillis: Long = 0,
): MutableStoreStateFlow<T> {
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
        debounceMillis = debounceMillis,
    )
}
