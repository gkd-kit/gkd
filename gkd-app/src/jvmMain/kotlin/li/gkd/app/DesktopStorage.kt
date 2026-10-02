package li.gkd.app

import kotlinx.serialization.json.Json
import li.gkd.app.storage.AppStorageLayout
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID


/** Paths are anchored by Gradle or the packaged launcher, never by the shell's working directory. */
object DesktopStorage {
    private val localRoot = (
        System.getProperty("jpackage.app-path")?.let { File(it).parentFile }
            ?: File(requireNotNull(System.getProperty("gkd.projectRoot")) {
                "Launch Desktop using Gradle or the packaged executable"
            })
        ).resolve(".local")
    val root = localRoot.resolve("desktop")
    val profile get() = sessionDirectory.resolve("profile")
    var sessionDirectory: File = root
        private set
    val isolated get() = sessionDirectory != root
    val data get() = sessionDirectory.resolve("data")
    val layout by lazy {
        AppStorageLayout(
            data.resolve("files"),
            data.resolve("cache"),
            data.resolve("files")
        )
    }

    fun initialize(test: Boolean) {
        sessionDirectory =
            if (test) localRoot.resolve("tests/desktop/${UUID.randomUUID()}") else root
        sessionDirectory.mkdirs()
    }
}

class DesktopSimulatorStorage(private val file: File) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun load(fallback: DesktopEnvironment): SimulatorSettings {
        if (!file.isFile) return SimulatorSettings().withEnvironment(fallback).persistent()
        return json.decodeFromString<SimulatorSettings>(file.readText()).persistent()
            .also { it.validate() }
    }

    fun save(settings: SimulatorSettings) {
        val value = settings.persistent()
        value.validate()
        file.parentFile.mkdirs()
        val temporary = Files.createTempFile(file.parentFile.toPath(), "simulator-", ".tmp")
        try {
            Files.writeString(temporary, json.encodeToString(value))
            try {
                Files.move(
                    temporary,
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

