package li.gkd.app.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files
import li.gkd.app.logging.LogUtils
import li.gkd.db.captureDatabaseLogSnapshot

object LogArchive {
    private val layout
        get() = appStorage()

    suspend fun build(
        metadata: Map<String, () -> String>,
        privilegeCrashFiles: List<File> = emptyList(),
        diagnosticDirectories: List<File> = emptyList(),
    ): File {
        val directory = StorageMaintenance.temporaryDirectory(layout)
        val database = directory.resolve("db")
        captureDatabaseLogSnapshot().writeJsonFiles(database)
        val metadataDirectory =
            Files.createDirectory(directory.resolve("metadata").toPath()).toFile()
        val privilegeCrashes = directory.resolve("priv-crash")
        if (privilegeCrashFiles.isNotEmpty()) {
            Files.createDirectory(privilegeCrashes.toPath())
            for (file in privilegeCrashFiles) {
                val target = privilegeCrashes.resolve(file.name).toPath()
                try {
                    Files.copy(file.toPath(), target)
                } catch (e: IOException) {
                    Files.deleteIfExists(target)
                    LogUtils.w("Unable to copy privilege crash report: ${file.name}", e)
                }
            }
        }
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
        return buildArchive(
            metadataDirectory,
            logs,
            listOf(database, privilegeCrashes) + diagnostics,
        )
    }

    private fun buildArchive(metadata: File, logs: File, additionalDirectories: List<File>): File {
        val files =
            (listOf(
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
