package li.gkd.app.settings

import java.util.UUID
import li.gkd.app.logging.LogUtils

interface SettingsStorage {
    fun read(filename: String): String?

    fun writeAtomically(filename: String, text: String)

    /** Preserve unreadable input before allowing a later write to replace it. */
    fun <T> readValue(filename: String, decode: (String?) -> T): T {
        val text = read(filename) ?: return decode(null)
        return try {
            decode(text)
        } catch (error: IllegalArgumentException) {
            val recoveryFile = "$filename.corrupt-${UUID.randomUUID()}"
            writeAtomically(recoveryFile, text)
            LogUtils.w("Settings decode failed: $filename; original saved as $recoveryFile", error)
            decode(null)
        }
    }
}

class SettingsWriteException(val filename: String, cause: Throwable) :
    Exception(cause.message ?: "Settings write failed: $filename", cause)
