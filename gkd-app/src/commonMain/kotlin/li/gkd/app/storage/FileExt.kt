package li.gkd.app.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun File.writeTextAtomically(text: String) {
    val temporary = File(parentFile, "$name.tmp")
    try {
        temporary.outputStream().use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } finally {
        temporary.delete()
    }
}
