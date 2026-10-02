package li.gkd.app.storage

import java.io.File
import java.io.IOException

object LogArchive {
    private val layout get() = appStorage()

    /** Material providers run inside the cleanup boundary, including failed collection. */
    fun build(metadata: Map<String, () -> String>): File {
        val directory = StorageMaintenance.temporaryDirectory(layout)
        var failure: Throwable? = null
        try {
            val files = metadata.map { (name, content) ->
                require(
                    name.isNotEmpty() && name != "." && name != ".." &&
                            '/' !in name && '\\' !in name && ':' !in name
                ) { "Invalid log entry: $name" }
                directory.resolve(name).apply { writeText(content()) }
            }
            return buildArchive(files)
        } catch (e: Throwable) {
            failure = e
            throw e
        } finally {
            if (!directory.deleteRecursively()) {
                val cleanup = IOException("Cannot delete log staging directory: $directory")
                failure?.addSuppressed(cleanup) ?: throw cleanup
            }
        }
    }

    private fun buildArchive(metadata: List<File>): File {
        val files = listOf(layout.db, layout.store, layout.subscription, layout.log, layout.crash)
            .filter { it.list()?.isNotEmpty() == true } + metadata
        val archive = ExportFileNames.reserve(
            layout.sharedCache,
            "log-${ExportFileNames.timestamp(System.currentTimeMillis())}",
            "zip"
        )
        try {
            check(ZipArchive.zipFiles(files, archive)); return archive
        } catch (e: Throwable) {
            archive.delete(); throw e
        }
    }
}
