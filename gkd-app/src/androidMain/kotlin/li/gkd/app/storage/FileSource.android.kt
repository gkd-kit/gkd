package li.gkd.app.storage

import androidx.core.net.toUri
import java.io.FileNotFoundException
import java.io.InputStream
import li.gkd.app.app

actual fun openFileSource(source: FileSource): InputStream =
    when (source) {
        is FileSource.Local -> source.file.inputStream()
        is FileSource.Uri ->
            app.contentResolver.openInputStream(source.value.toUri())
                ?: throw FileNotFoundException("Cannot open selected document")
    }
