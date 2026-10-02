package li.gkd.app.settings

interface SettingsStorage {
    fun read(filename: String): String?
    suspend fun writeAtomically(filename: String, text: String)
}

class SettingsWriteException(val filename: String, cause: Throwable) :
    Exception(cause.message ?: "Settings write failed: $filename", cause)

object SettingsAppIds {
    private val validId = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    fun decode(text: String): Set<String> = text.split('\n').filter { validId.matches(it) }.toSet()
    fun encode(ids: Set<String>): String = ids.sorted().joinToString("\n")
}
