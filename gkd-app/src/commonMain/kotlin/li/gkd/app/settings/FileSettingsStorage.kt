package li.gkd.app.settings

import li.gkd.app.storage.writeTextAtomically
import java.io.File

/** Same durable format on Android and Desktop; paths belong to the host. */
class FileSettingsStorage(private val directory: File) : SettingsStorage {
    override fun read(filename: String): String? =
        directory.resolve(filename).takeIf { it.exists() }?.readText()

    override fun writeAtomically(filename: String, text: String) {
        directory.resolve(filename).writeTextAtomically(text)
    }
}
