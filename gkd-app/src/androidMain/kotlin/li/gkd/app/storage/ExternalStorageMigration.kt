package li.gkd.app.storage

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The journal commits each phase before its destination is opened by the app. */
class ExternalStorageMigration(private val destination: File, private val work: File) {
    private val journal = work.resolve("journal.json")
    private val marker = destination.resolve(".gkd")

    @Serializable
    private data class Entry(val source: String, val target: String, val digest: String)

    @Serializable
    private data class HistoryProgress(val files: List<Entry>, val committed: Boolean = false)

    @Serializable
    private data class Journal(
        val source: String,
        val core: List<Entry>,
        val coreCommitted: Boolean = false,
        val history: Map<HistoryDirectory, HistoryProgress> = emptyMap(),
    ) {
        val historyComplete
            get() = HistoryDirectory.entries.all { history[it]?.committed == true }
    }

    private data class JournalUpdate(
        val directory: HistoryDirectory,
        val progress: HistoryProgress,
        val saved: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    fun prepare(externalFiles: () -> File?): DirectorySelectionRecord {
        val marked = marker.isFile
        if (marked) {
            return selection(true, null, StorageSelectionReason.InternalMarkerExists)
        }

        val previous = readJournal()
        val external = if (previous == null) externalFiles() else File(previous.source)
        if (previous == null && (external == null || !hasData(external))) {
            val reason =
                if (external == null) StorageSelectionReason.ExternalNullFallback
                else StorageSelectionReason.ExternalEmpty
            return selection(false, external, reason, marker.createNewFile())
        }

        val state =
            previous
                ?: checkNotNull(external).let { source ->
                    Files.createDirectories(work.toPath())
                    Journal(source.absolutePath, stage(source, coreDirectories)).also(::saveJournal)
                }
        if (!state.coreCommitted) {
            // Never pair the external database with an old internal WAL or shared-memory index.
            if (state.core.any { it.target == "db/gkd.db" }) {
                listOf("db/gkd.db-wal", "db/gkd.db-shm", "db/gkd.db.lck").forEach { path ->
                    if (state.core.none { it.target == path }) {
                        Files.deleteIfExists(destination.resolve(path).toPath())
                    }
                }
            }
            publish(state.core)
            saveJournal(state.copy(coreCommitted = true))
        }
        return selection(false, external, StorageSelectionReason.ExternalMigration)
    }

    suspend fun migrateHistory(
        externalFiles: () -> File?,
        onFinished: suspend (HistoryDirectory, Result<Unit>) -> Unit,
    ): Boolean = coroutineScope {
        val state = checkNotNull(readJournal())
        check(state.coreCommitted)
        val source =
            if (state.history.size < HistoryDirectory.entries.size) externalFiles() else null
        val updates = Channel<JournalUpdate>()
        val journalWriter = async {
            var latest = state
            for (update in updates) {
                latest =
                    latest.copy(history = latest.history + (update.directory to update.progress))
                saveJournal(latest)
                update.saved.complete(Unit)
            }
            latest
        }

        suspend fun persist(directory: HistoryDirectory, progress: HistoryProgress) {
            val update = JournalUpdate(directory, progress)
            updates.send(update)
            update.saved.await()
        }

        suspend fun migrate(vararg directories: HistoryDirectory) {
            for (directory in directories) {
                val result =
                    try {
                        val progress =
                            state.history[directory]
                                ?: HistoryProgress(
                                        stage(requireSource(state, source), listOf(directory.path)),
                                    )
                                    .also {
                                        persist(directory, it)
                                    }
                        if (!progress.committed) {
                            publish(progress.files, keepStaged = directory == HistoryDirectory.Log)
                            persist(directory, progress.copy(committed = true))
                        }
                        Result.success(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                onFinished(directory, result)
            }
        }

        val snapshots = launch { migrate(HistoryDirectory.Snapshot) }
        val other = launch {
            migrate(HistoryDirectory.MatchDiagnostics, HistoryDirectory.Crash, HistoryDirectory.Log)
        }
        joinAll(snapshots, other)
        updates.close()
        journalWriter.await().historyComplete
    }

    fun cleanUp(externalFiles: () -> File?) {
        val state = checkNotNull(readJournal())
        check(state.historyComplete)
        val source = requireSource(state, externalFiles())
        val sourcePath = source.toPath().toRealPath()
        val entries = state.core + state.history.values.flatMap { it.files }
        val originals = entries.mapNotNull { entry ->
            val file = source.resolve(entry.source)
            if (Files.notExists(file.toPath(), NOFOLLOW_LINKS)) return@mapNotNull null
            if (
                !Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) ||
                    !file.toPath().toRealPath().startsWith(sourcePath) ||
                    file.digest() != entry.digest
            ) {
                throw IOException("External migration source changed: ${entry.source}")
            }
            file
        }
        originals.forEach { Files.deleteIfExists(it.toPath()) }
        entries
            .map { checkNotNull(source.resolve(it.source).parentFile) }
            .flatMap { parent ->
                generateSequence(parent) { it.parentFile }.takeWhile { it != source }.toList()
            }
            .distinct()
            .sortedByDescending { it.path.length }
            .forEach { directory ->
                val path = directory.toPath()
                if (
                    Files.isDirectory(path, NOFOLLOW_LINKS) &&
                        path.toRealPath().startsWith(sourcePath) &&
                        directory.list()?.isEmpty() == true
                ) {
                    Files.deleteIfExists(path)
                }
            }
        marker.createNewFile()
        if (!work.deleteRecursively())
            throw IOException("Cannot clean up storage migration staging")
    }

    private fun selection(
        marked: Boolean,
        external: File?,
        reason: StorageSelectionReason,
        markerCreated: Boolean? = null,
    ) =
        DirectorySelectionRecord(
            markerBefore = marked,
            externalResult = external?.absolutePath,
            reason = reason,
            markerCreated = markerCreated,
            selectedPath = destination.absolutePath,
            storageLocation = StorageLocation.Internal,
        )

    private fun readJournal(): Journal? =
        if (journal.isFile) Json.decodeFromString<Journal>(journal.readText()) else null

    private fun saveJournal(state: Journal) =
        journal.writeTextAtomically(Json.encodeToString(state))

    private fun requireSource(state: Journal, source: File?): File {
        if (source == null || source.absolutePath != state.source || !source.isDirectory) {
            throw IOException("External migration source is unavailable: ${state.source}")
        }
        return source
    }

    private fun hasData(source: File): Boolean {
        if (!source.isDirectory) throw IOException("Cannot inspect external data: $source")
        return (coreDirectories + HistoryDirectory.entries.map { it.path }).any { root ->
            val directory = source.resolve(root).toPath()
            if (Files.notExists(directory, NOFOLLOW_LINKS)) false
            else Files.list(directory).use { it.findAny().isPresent }
        }
    }

    private fun stage(source: File, directories: List<String>): List<Entry> = buildList {
        for (root in directories) {
            val directory = source.resolve(root).toPath()
            if (Files.notExists(directory, NOFOLLOW_LINKS)) continue
            check(Files.isDirectory(directory, NOFOLLOW_LINKS)) {
                "Invalid migration directory: $directory"
            }
            Files.find(directory, Int.MAX_VALUE, { _, attributes -> attributes.isRegularFile })
                .use { paths ->
                    paths.forEach { path ->
                        val file = path.toFile()
                        val relative = file.relativeTo(source).invariantSeparatorsPath
                        // SQLite recreates its shared-memory index and Room lock file.
                        if (relative == "db/gkd.db-shm" || relative == "db/gkd.db.lck")
                            return@forEach
                        val target =
                            if (root == HistoryDirectory.Log.path && relative.endsWith(".log"))
                                relative.removeSuffix(".log") + "-migrated.log"
                            else relative
                        val staged = work.resolve(target)
                        Files.createDirectories(checkNotNull(staged.parentFile).toPath())
                        val sourceDigest = MessageDigest.getInstance("SHA-256")
                        DigestInputStream(Files.newInputStream(path), sourceDigest).use {
                            Files.copy(it, staged.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        Files.setLastModifiedTime(staged.toPath(), Files.getLastModifiedTime(path))
                        staged.forceToDisk()
                        val digest = sourceDigest.digest().toHexString()
                        if (staged.digest() != digest)
                            throw IOException("Cannot verify migrated file: $relative")
                        add(Entry(relative, target, digest))
                    }
                }
        }
    }

    private fun publish(entries: List<Entry>, keepStaged: Boolean = false) {
        for (entry in entries) {
            val staged = work.resolve(entry.target)
            val target = destination.resolve(entry.target)
            val targetPath = target.toPath()
            if (!Files.notExists(targetPath, NOFOLLOW_LINKS)) {
                try {
                    val attributes =
                        Files.readAttributes(
                            targetPath,
                            BasicFileAttributes::class.java,
                            NOFOLLOW_LINKS,
                        )
                    if (attributes.isRegularFile && target.digest() == entry.digest) continue
                } catch (e: NoSuchFileException) {
                    // Log retention can remove an already published log while migration resumes.
                    if (!keepStaged) throw e
                }
            }
            Files.createDirectories(checkNotNull(target.parentFile).toPath())
            val ready =
                if (keepStaged) {
                    val copy = Files.createTempFile(work.toPath(), "log-", ".publish").toFile()
                    Files.copy(
                        staged.toPath(),
                        copy.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES,
                    )
                    copy.forceToDisk()
                    copy
                } else staged
            Files.move(
                ready.toPath(),
                targetPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun File.digest(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(toPath()).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHexString()
    }

    private fun File.forceToDisk() {
        FileChannel.open(toPath(), StandardOpenOption.WRITE).use { it.force(true) }
    }

    private companion object {
        val coreDirectories = listOf("db", "store", "subscription")
    }
}
