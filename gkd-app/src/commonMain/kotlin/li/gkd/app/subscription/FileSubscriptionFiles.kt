package li.gkd.app.subscription

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileSubscriptionFiles(
    private val directory: File,
) : SubscriptionFiles {
    init {
        directory.mkdirs()
    }

    private fun file(id: Long) = directory.resolve("$id.json")
    override fun load(id: Long): RawSubscription {
        val bytes = readBytes(id) ?: throw SubscriptionException(
            SubscriptionFailureReason.SubscriptionFileMissing,
        )
        val subscription = try {
            RawSubscription.parse(bytes.decodeToString(), json5 = false)
        } catch (e: Exception) {
            throw SubscriptionException(SubscriptionFailureReason.SubscriptionFileParseFailed, cause = e)
        }
        check(subscription.id == id) {
            throw SubscriptionException(SubscriptionFailureReason.SubscriptionFileIdMismatch)
        }
        return subscription
    }

    @Synchronized
    override fun readBytes(id: Long): ByteArray? {
        val target = file(id)
        // Preserve Android AtomicFile recovery artifacts when reusing an existing directory.
        val backup = directory.resolve("$id.json.bak")
        if (backup.isFile) Files.move(
            backup.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
        Files.deleteIfExists(directory.resolve("$id.json.new").toPath())
        return target.takeIf { it.isFile }?.readBytes()
    }

    override fun write(subscription: RawSubscription) = writeBytes(
        subscription.id,
        SubscriptionJson.json.encodeToString(subscription).encodeToByteArray()
    )

    override fun restore(id: Long, bytes: ByteArray?) {
        if (bytes == null) delete(id) else writeBytes(id, bytes)
    }

    @Synchronized
    override fun delete(id: Long) {
        val target = file(id)
        Files.deleteIfExists(directory.resolve("$id.json.bak").toPath())
        Files.deleteIfExists(directory.resolve("$id.json.new").toPath())
        check(!target.exists() || target.delete()) {
            throw SubscriptionException(SubscriptionFailureReason.FileDeleteFailed, listOf(target.name))
        }
    }

    @Synchronized
    private fun writeBytes(id: Long, bytes: ByteArray) {
        readBytes(id) // Recover an interrupted Android write before starting the next one.
        val temporary = Files.createTempFile(directory.toPath(), "$id-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { it.write(bytes); it.fd.sync() }
            try {
                Files.move(
                    temporary,
                    file(id).toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file(id).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
