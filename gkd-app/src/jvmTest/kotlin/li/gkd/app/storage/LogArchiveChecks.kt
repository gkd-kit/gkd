package li.gkd.app.storage

import java.io.IOException
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import li.gkd.app.matching.diagnostics.MatchDiagnosticFiles
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.db.Db

class LogArchiveChecks {
    fun archiveContainsMaterialsAndExpiredCacheIsCleanedByMaintenance() {
        try {
            val layout = appStorage()
            val privilegeCrash = layout.tempCache.resolve("priv-crash").apply { mkdirs() }
            privilegeCrash
                .resolve("priv-crash_uid2000_20261007001234.json")
                .writeText("{\"processType\":\"server\"}")
            val previous = layout.tempCache.listFiles().orEmpty().toSet()
            layout.log.resolve("event.txt").writeText("event")
            val diagnostics = MatchDiagnostics()
            val session = diagnostics.start(MatchTimingPolicy())
            diagnostics.stop(session.id)
            MatchDiagnosticFiles.enqueue(diagnostics.export(session.id))
            kotlinx.coroutines.runBlocking { MatchDiagnosticFiles.flush() }
            layout.startupLog
                .apply { mkdirs() }
                .resolve("gkd-startup-20261006162246.json")
                .writeText("{\"reason\":\"external_null_fallback\"}")
            val captureDirectory =
                layout.matchingDiagnostics.listFiles()!!.single {
                    it.name == session.startedAt.toString()
                }
            captureDirectory
                .resolve("attachments")
                .apply { mkdirs() }
                .resolve("evidence.txt")
                .writeText("evidence")
            val record =
                kotlinx.coroutines
                    .runBlocking { Db.matchDiagnosticRecordDao.observeAll().first() }
                    .single {
                        it.id == session.startedAt
                    }
            val second = diagnostics.start(MatchTimingPolicy())
            diagnostics.stop(second.id)
            MatchDiagnosticFiles.enqueue(diagnostics.export(second.id))
            kotlinx.coroutines.runBlocking { MatchDiagnosticFiles.flush() }
            val singleArchive = kotlinx.coroutines.runBlocking { createLogArchive(listOf(record)) }
            assertTrue(Regex("gkd-log-[0-9]{14}(-[0-9]+)?\\.zip").matches(singleArchive.name))
            ZipFile(singleArchive).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                assertTrue(
                    "matching-diagnostics/${captureDirectory.name}/attachments/evidence.txt" in
                        names,
                )
                assertTrue(names.any { it.startsWith("metadata/") })
                assertTrue(names.any { it.endsWith("event.txt") })
                assertFalse(names.any { it.contains("/${second.startedAt}/") })
            }
            val selectedRecords =
                kotlinx.coroutines
                    .runBlocking { Db.matchDiagnosticRecordDao.observeAll().first() }
                    .filter { it.id in setOf(record.id, second.startedAt) }
            val batchArchive = kotlinx.coroutines.runBlocking { createLogArchive(selectedRecords) }
            ZipFile(batchArchive).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                assertEquals(
                    2,
                    names.count {
                        it.startsWith("matching-diagnostics/") && it.endsWith("/diagnostic.json")
                    },
                )
            }
            // About-page export includes every saved capture and its attachments.
            val allArchive = kotlinx.coroutines.runBlocking { createLogArchive() }
            ZipFile(allArchive).use { zip ->
                assertTrue(
                    zip.getEntry("matching-diagnostics/${session.startedAt}/diagnostic.json") !=
                        null,
                )
                assertTrue(
                    zip.getEntry("matching-diagnostics/${second.startedAt}/diagnostic.json") !=
                        null,
                )
                assertEquals(
                    "evidence",
                    zip.getInputStream(
                            zip.getEntry(
                                "matching-diagnostics/${session.startedAt}/attachments/evidence.txt",
                            ),
                        )
                        .bufferedReader()
                        .readText(),
                )
            }
            val previousNormal = layout.tempCache.listFiles().orEmpty().toSet()
            val archive = LogArchive.build(mapOf("apps.json" to { "[]" }), listOf(privilegeCrash))
            ZipFile(archive).use { zip ->
                assertEquals(
                    "[]",
                    zip.getInputStream(zip.getEntry("metadata/apps.json"))
                        .bufferedReader()
                        .readText(),
                )
                assertNull(zip.getEntry("apps.json"))
                assertFalse(
                    zip.entries().asSequence().any { it.name.startsWith("matching-diagnostics/") },
                )
                assertEquals(
                    "{\"processType\":\"server\"}",
                    zip.getInputStream(
                            zip.getEntry("priv-crash/priv-crash_uid2000_20261007001234.json"),
                        )
                        .bufferedReader()
                        .readText(),
                )
                assertTrue(zip.entries().asSequence().any { it.name.endsWith("event.txt") })
                assertEquals(
                    "{\"reason\":\"external_null_fallback\"}",
                    zip.getInputStream(zip.getEntry("startup-log/gkd-startup-20261006162246.json"))
                        .bufferedReader()
                        .readText(),
                )
            }
            kotlinx.coroutines.runBlocking {
                val files = MatchDiagnosticFiles
                files.delete(listOf(record))
                assertFalse(captureDirectory.exists())
                files.enqueue(diagnostics.export(session.id))
                files.flush()
                assertFalse(
                    captureDirectory.exists(),
                    "Queued updates must not restore a deleted record",
                )
                assertFalse(
                    Db.matchDiagnosticRecordDao.observeAll().first().any { it.id == record.id },
                )
            }
            val staging = layout.tempCache.listFiles()!!.single { it !in previousNormal }
            assertEquals("[]", staging.resolve("metadata/apps.json").readText())
            layout.tempCache
                .listFiles()
                .orEmpty()
                .filter { it !in previous }
                .forEach { assertTrue(it.setLastModified(1)) }
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
            for (error in
                listOf(
                    IOException("host collection failed"),
                    IllegalStateException("unexpected collector failure"),
                )) {
                val previous = layout.tempCache.listFiles().orEmpty().toSet()
                val actual =
                    assertFailsWith<Exception> {
                        LogArchive.build(
                            linkedMapOf(
                                "first.txt" to { "collected" },
                                "failed.txt" to { throw error },
                            ),
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
