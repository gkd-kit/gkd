package li.gkd.app.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files

object StartupLogs {
    fun write(directory: File, time: Long, content: String) {
        Files.createDirectories(directory.toPath())
        val name = "gkd-startup-" + ExportFileNames.timestamp(time) + ".json"
        directory.resolve(name).writeText(content)
        val files = directory.listFiles() ?: throw IOException("Cannot list directory: $directory")
        files
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .drop(30)
            .forEach {
                Files.delete(it.toPath())
            }
    }
}
