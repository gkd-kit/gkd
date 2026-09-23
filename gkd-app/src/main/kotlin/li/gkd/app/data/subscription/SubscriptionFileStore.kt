package li.gkd.app.data.subscription

import android.util.AtomicFile
import li.gkd.app.text.UiStrings
import li.gkd.app.data.RawSubscription
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.json
import li.gkd.db.LOCAL_HTTP_SUBS_ID
import li.gkd.db.LOCAL_SUBS_ID
import java.io.File
import java.io.FileOutputStream

object SubscriptionFileStore {
    fun load(id: Long): RawSubscription {
        val file = file(id)
        if (!file.exists()) {
            return when (id) {
                LOCAL_SUBS_ID -> RawSubscription(id = id, name = UiStrings.subscription_local, version = 0)
                LOCAL_HTTP_SUBS_ID -> RawSubscription(id = id, name = UiStrings.subscription_memory, version = 0)
                else -> error(UiStrings.subscription_file_missing)
            }
        }
        val subscription = try {
            RawSubscription.parse(file.readText(), json5 = false)
        } catch (e: Exception) {
            throw Exception(UiStrings.subscription_file_parse_failed, e)
        }
        if (subscription.id != id) error(UiStrings.subscription_file_id_mismatch)
        return subscription
    }

    fun readBytes(id: Long): ByteArray? = file(id).takeIf { it.exists() }?.readBytes()

    fun write(subscription: RawSubscription) {
        writeBytes(subscription.id, json.encodeToString(subscription).encodeToByteArray())
    }

    fun restore(id: Long, bytes: ByteArray?) {
        if (bytes == null) {
            delete(id)
        } else {
            writeBytes(id, bytes)
        }
    }

    fun delete(id: Long) {
        val file = file(id)
        AtomicFile(file).delete()
        if (file.exists()) error(UiStrings.file_delete_failed(file.name))
    }

    private fun file(id: Long): File = FolderUtils.subsFolder.resolve("$id.json")

    private fun writeBytes(id: Long, bytes: ByteArray) {
        val atomicFile = AtomicFile(file(id))
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            throw e
        }
    }
}
