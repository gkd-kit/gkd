package li.gkd.app.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import li.gkd.app.subscription.FileSubscriptionFiles
import li.gkd.app.subscription.SubscriptionException
import li.gkd.app.subscription.SubscriptionFailureReason

class SharedStorageBehaviorTest {
    @Test
    fun subscriptionReaderReportsMissingAndCorruptFilesWithoutInventingDefaultContent() {
        val root = Files.createTempDirectory("gkd-subscription-read").toFile()
        try {
            val files = FileSubscriptionFiles(root)
            for (id in listOf(li.gkd.db.LOCAL_SUBS_ID, li.gkd.db.LOCAL_HTTP_SUBS_ID, 42L)) {
                val error = assertFailsWith<SubscriptionException> { files.load(id) }
                assertEquals(SubscriptionFailureReason.SubscriptionFileMissing, error.reason)
                assertFalse(root.resolve("$id.json").exists())
            }
            val file = root.resolve("42.json").apply { writeText("{") }
            val error = assertFailsWith<SubscriptionException> { files.load(42) }
            assertEquals(SubscriptionFailureReason.SubscriptionFileParseFailed, error.reason)
            assertNotNull(error.cause)
            assertEquals("{", file.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun subscriptionStorageRecoversAndroidBackupAndDeletesAllRecoveryArtifacts() {
        val root = Files.createTempDirectory("gkd-subscription-recovery").toFile()
        try {
            val files = FileSubscriptionFiles(root)
            root.resolve("1.json").writeText("incomplete")
            root.resolve("1.json.bak").writeText("original")
            root.resolve("1.json.new").writeText("pending")
            assertEquals("original", files.readBytes(1)!!.decodeToString())
            assertFalse(root.resolve("1.json.new").exists())
            root.resolve("1.json.bak").writeText("original")
            files.delete(1)
            assertNull(files.readBytes(1))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cacheCleanupLeavesRecentFilesAndPersistentDataUntouched() {
        val root = Files.createTempDirectory("gkd-cache-cleanup").toFile()
        try {
            val layout = AppStorageLayout(
                root.resolve("files"),
                root.resolve("cache"),
                root.resolve("files")
            )
            val old = layout.tempCache.resolve("old").apply { writeText("old"); setLastModified(1) }
            val recent = layout.sharedCache.resolve("new").apply { writeText("new") }
            val config =
                layout.store.resolve("store.json").apply { writeText("{}"); setLastModified(1) }
            StorageMaintenance.clearExpired(layout)
            assertFalse(old.exists())
            assertTrue(recent.exists())
            assertTrue(config.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
