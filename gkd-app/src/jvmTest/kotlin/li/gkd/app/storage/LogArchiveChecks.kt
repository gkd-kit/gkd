package li.gkd.app.storage

import java.io.IOException
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogArchiveChecks {
    fun archiveContainsMaterialsAndExpiredCacheIsCleanedByMaintenance() {
        try {
            val layout = appStorage()
            val privilegeCrash = layout.tempCache.resolve("priv-crash").apply { mkdirs() }
            privilegeCrash.resolve("priv-crash_uid2000_20261007001234.json").writeText("{\"processType\":\"server\"}")
            val previous = layout.tempCache.listFiles().orEmpty().toSet()
            layout.log.resolve("event.txt").writeText("event")
            layout.startupLog.apply { mkdirs() }.resolve("gkd-startup-20261006162246.json").writeText("{\"reason\":\"external_null_fallback\"}")
            val archive = LogArchive.build(mapOf("apps.json" to { "[]" }), listOf(privilegeCrash))
            ZipFile(archive).use { zip ->
                assertEquals(
                    "[]",
                    zip.getInputStream(zip.getEntry("metadata/apps.json")).bufferedReader().readText()
                )
                assertNull(zip.getEntry("apps.json"))
                assertEquals(
                    "{\"processType\":\"server\"}",
                    zip.getInputStream(zip.getEntry("priv-crash/priv-crash_uid2000_20261007001234.json")).bufferedReader().readText(),
                )
                assertTrue(zip.entries().asSequence().any { it.name.endsWith("event.txt") })
                assertEquals(
                    "{\"reason\":\"external_null_fallback\"}",
                    zip.getInputStream(zip.getEntry("startup-log/gkd-startup-20261006162246.json")).bufferedReader().readText()
                )
            }
            val staging = layout.tempCache.listFiles()!!.single { it !in previous }
            assertEquals("[]", staging.resolve("metadata/apps.json").readText())
            assertTrue(staging.setLastModified(1))
            assertTrue(archive.setLastModified(1))
            StorageMaintenance.clearExpired(layout)
            assertFalse(staging.exists())
            assertFalse(archive.exists())
        } finally {
            appStorage().log.resolve("event.txt").delete()
            appStorage().startupLog.resolve("gkd-startup-20261006162246.json").delete()
            appStorage().tempCache.resolve("priv-crash").deleteRecursively()
        }
    }

    fun materialFailurePreservesCauseAndLeavesFilesForCacheMaintenance() {
        try {
            val layout = appStorage()
            for (error in listOf(IOException("host collection failed"), IllegalStateException("unexpected collector failure"))) {
                val previous = layout.tempCache.listFiles().orEmpty().toSet()
                val actual = assertFailsWith<Exception> {
                    LogArchive.build(
                        linkedMapOf(
                            "first.txt" to { "collected" }, "failed.txt" to { throw error },
                        )
                    )
                }
                assertSame(error, actual)
                val staging = layout.tempCache.listFiles()!!.single { it !in previous }
                assertEquals("collected", staging.resolve("metadata/first.txt").readText())
                assertTrue(staging.setLastModified(1))
                StorageMaintenance.clearExpired(layout)
                assertFalse(staging.exists())
            }

        } finally {
            appStorage().log.resolve("event.txt").delete()
        }
    }
}
