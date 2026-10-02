package li.gkd.app.settings

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Same durable format on Android and Desktop; paths belong to the host. */
class FileSettingsStorage(private val directory: File) : SettingsStorage {
    private fun resolve(filename: String): File {
        require(filename.isNotEmpty() && '/' !in filename && '\\' !in filename && filename != "." && filename != "..")
        return directory.resolve(filename)
    }

    override fun read(filename: String): String? =
        resolve(filename).takeIf { it.exists() }?.readText()

    override suspend fun writeAtomically(filename: String, text: String) {
        val destination = resolve(filename)
        val temporary = resolve("$filename.tmp")
        try {
            temporary.outputStream()
                .use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } finally {
            temporary.delete()
        }
    }
}
