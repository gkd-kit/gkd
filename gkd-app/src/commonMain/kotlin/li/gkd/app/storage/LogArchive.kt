package li.gkd.app.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files

object LogArchive {
    private val layout get() = appStorage()

    fun build(metadata: Map<String, () -> String>, additionalDirectories: List<File> = emptyList()): File {
        val directory = StorageMaintenance.temporaryDirectory(layout)
        val metadataDirectory = Files.createDirectory(directory.resolve("metadata").toPath()).toFile()
        metadata.forEach { (name, content) ->
            metadataDirectory.resolve(name).writeText(content())
        }
        return buildArchive(metadataDirectory, additionalDirectories)
    }

    private fun buildArchive(metadata: File, additionalDirectories: List<File>): File {
        val files = (listOf(layout.db, layout.store, layout.subscription, layout.log, layout.crash, layout.startupLog) + additionalDirectories)
            .filter { directory ->
                if (Files.notExists(directory.toPath())) return@filter false
                val entries = directory.list()
                    ?: throw IOException("Cannot list directory: $directory")
                entries.isNotEmpty()
            } + metadata
        val archive = ExportFileNames.reserve(
            layout.sharedCache,
            "gkd-log-${ExportFileNames.timestamp(System.currentTimeMillis())}",
            "zip"
        )
        ZipArchive.zipFiles(files, archive)
        return archive
    }
}
