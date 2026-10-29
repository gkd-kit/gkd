package li.gkd.app.storage

import java.io.File
import java.nio.file.Files
import li.gkd.app.logging.LogUtils

object LogArchive {
    private val layout
        get() = appStorage()

    fun build(
        metadata: Map<String, () -> String>,
        additionalDirectories: List<File> = emptyList(),
        diagnosticDirectories: List<File> = emptyList(),
    ): File {
        val directory = StorageMaintenance.temporaryDirectory(layout)
        val metadataDirectory =
            Files.createDirectory(directory.resolve("metadata").toPath()).toFile()
        val logs = directory.resolve("log")
        LogUtils.copyLogs(logs)
        metadata.forEach { (name, content) ->
            metadataDirectory.resolve(name).writeText(content())
        }
        val diagnostics =
            if (diagnosticDirectories.isEmpty()) emptyList()
            else {
                val target = directory.resolve("matching-diagnostics").apply { mkdirs() }
                diagnosticDirectories.forEach { source ->
                    check(source.copyRecursively(target.resolve(source.name)))
                }
                listOf(target)
            }
        return buildArchive(metadataDirectory, logs, additionalDirectories + diagnostics)
    }

    private fun buildArchive(metadata: File, logs: File, additionalDirectories: List<File>): File {
        val files =
            (listOf(
                    layout.db,
                    layout.store,
                    layout.subscription,
                    logs,
                    layout.crash,
                    layout.startupLog,
                ) + additionalDirectories)
                .filter { directory -> !Files.notExists(directory.toPath()) } + metadata
        val archive =
            ExportFileNames.reserve(
                layout.sharedCache,
                "gkd-log-${ExportFileNames.timestamp(System.currentTimeMillis())}",
                "zip",
            )
        ZipArchive.zipFiles(files, archive, includeEmptyDirectories = false)
        return archive
    }
}
