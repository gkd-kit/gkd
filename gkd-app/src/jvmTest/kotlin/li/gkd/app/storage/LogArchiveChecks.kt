package li.gkd.app.storage

import java.io.IOException
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogArchiveChecks {
    fun archiveContainsHostMaterialsAndProductionLogsAndCleansStaging() {
        try {
            val layout = appStorage()
            layout.log.resolve("event.txt").writeText("event")
            val archive = LogArchive.build(mapOf("apps.json" to { "[]" }))
            ZipFile(archive).use { zip ->
                assertEquals(
                    "[]",
                    zip.getInputStream(zip.getEntry("apps.json")).bufferedReader().readText()
                )
                assertTrue(zip.entries().asSequence().any { it.name.endsWith("event.txt") })
            }
            assertTrue(layout.tempCache.listFiles().orEmpty().isEmpty())
            archive.delete()
        } finally {
            appStorage().log.resolve("event.txt").delete()
        }
    }

    fun materialFailurePreservesCauseAndCleansAlreadyWrittenFiles() {
        try {
            val layout = appStorage()
            val error = IOException("host collection failed")
            val actual = assertFailsWith<IOException> {
                LogArchive.build(
                    linkedMapOf(
                        "first.txt" to { "collected" }, "failed.txt" to { throw error },
                    )
                )
            }
            assertSame(error, actual)
            assertTrue(layout.tempCache.listFiles().orEmpty().isEmpty())

        } finally {
            appStorage().log.resolve("event.txt").delete()
        }
    }
}
