package li.gkd.app.storage

import java.io.File
import java.io.InputStream
import java.net.URI

actual fun openFileSource(source: FileSource): InputStream = when (source) {
    is FileSource.Local -> source.file.inputStream()
    is FileSource.Uri -> {
        val uri = URI(source.value)
        require(uri.scheme.equals("file", ignoreCase = true)) { "Unsupported file source" }
        File(uri).inputStream()
    }
}
