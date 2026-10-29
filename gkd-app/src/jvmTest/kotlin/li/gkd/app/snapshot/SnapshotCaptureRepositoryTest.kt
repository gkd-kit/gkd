package li.gkd.app.snapshot

import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.DeviceInfo
import li.gkd.app.model.ScreenSize
import li.gkd.app.platform.PlatformResult
import li.gkd.app.storage.AppStorageLayout
import li.gkd.db.Db

class SnapshotCaptureRepositoryTest(private val layout: AppStorageLayout) {
    fun missingScreenshotStillCommitsCompatibleFilesAndMetadata() = runBlocking {
        fixture { f ->
            val result =
                assertIs<SnapshotCaptureOutcome.Captured<SnapshotCaptureResult>>(
                        f.capture(false) { input(SnapshotScreenshotStatus.Unavailable) },
                    )
                    .value
            assertEquals(SnapshotScreenshotStatus.Unavailable, result.screenshotStatus)
            assertIs<SnapshotAutoExportResult.Disabled>(result.autoExport)
            val saved =
                Json.decodeFromString<ComplexSnapshot>(
                    SnapshotStore.snapshotFile(result.snapshot.id).readText(),
                )
            assertEquals(result.snapshot, saved)
            assertEquals(listOf(saved.toSnapshot()), Db.snapshotDao.query().first())
            assertContentEquals(
                input().screenshot,
                SnapshotStore.screenshotFile(saved.id).readBytes(),
            )
        }
    }

    fun overlappingCaptureIsRejectedAndCancellationReleasesLock() = runBlocking {
        fixture { f ->
            val entered = CompletableDeferred<Unit>()
            val job = launch {
                f.capture(false) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            withTimeout(10_000) { entered.await() }
            assertTrue(f.isCapturing)
            assertEquals(
                SnapshotCaptureOutcome.Busy,
                f.capture(false) { error("Duplicate collector must not run") },
            )
            job.cancelAndJoin()
            assertFalse(f.isCapturing)
            assertTrue(Db.snapshotDao.query().first().isEmpty())
            f.capture(false) { input() }
            assertEquals(1, Db.snapshotDao.query().first().size)
        }
    }

    fun collectorFailureDoesNotSaveAndNextCaptureCanSucceed() = runBlocking {
        fixture { f ->
            assertFailsWith<IOException> { f.capture(false) { throw IOException("nodes") } }
            assertTrue(Db.snapshotDao.query().first().isEmpty())
            assertFalse(f.isCapturing)
            f.capture(false) { input() }
            assertEquals(1, Db.snapshotDao.query().first().size)
        }
    }

    fun databaseFailureRollsBackFilesAndAllowsRetry() = runBlocking {
        fixture { f ->
            withSnapshotDbFailure(layout.database, "INSERT") {
                assertFails { f.capture(false) { input() } }
            }
            assertTrue(layout.snapshot.listFiles().orEmpty().isEmpty())
            assertFalse(f.isCapturing)
            f.capture(false) { input() }
            assertEquals(1, Db.snapshotDao.query().first().size)
        }
    }

    fun disabledExportCommitsSnapshotWithoutCreatingAnArchive() = runBlocking {
        fixture { repository ->
            val result =
                assertIs<SnapshotCaptureOutcome.Captured<SnapshotCaptureResult>>(
                        repository.capture(false) { input() },
                    )
                    .value
            assertIs<SnapshotAutoExportResult.Disabled>(result.autoExport)
            assertTrue(layout.sharedCache.listFiles().orEmpty().isEmpty())
            assertEquals(1, Db.snapshotDao.query().first().size)
        }
    }

    fun unsupportedExportPreservesSnapshotAndCleansArchive() = runBlocking {
        fixture { repository ->
            val result =
                assertIs<SnapshotCaptureOutcome.Captured<SnapshotCaptureResult>>(
                        repository.capture(true) {
                            input(SnapshotScreenshotStatus.LikelyProtected)
                        },
                    )
                    .value
            assertEquals(
                PlatformResult.Unsupported,
                assertIs<SnapshotAutoExportResult.Completed>(result.autoExport).result,
            )
            assertEquals(SnapshotScreenshotStatus.LikelyProtected, result.screenshotStatus)
            assertTrue(layout.sharedCache.listFiles().orEmpty().isEmpty())
            assertEquals(1, Db.snapshotDao.query().first().size)
        }
    }

    fun runAll() {
        missingScreenshotStillCommitsCompatibleFilesAndMetadata()
        overlappingCaptureIsRejectedAndCancellationReleasesLock()
        collectorFailureDoesNotSaveAndNextCaptureCanSucceed()
        databaseFailureRollsBackFilesAndAllowsRetry()
        disabledExportCommitsSnapshotWithoutCreatingAnArchive()
        unsupportedExportPreservesSnapshotAndCleansArchive()
    }

    private fun input(status: SnapshotScreenshotStatus = SnapshotScreenshotStatus.Captured) =
        SnapshotCaptureInput(
            "dev.capture",
            "dev.capture.Main",
            ScreenSize(2, 2),
            false,
            emptyList(),
            null,
            null,
            DeviceInfo("test", "test", "test", "test", 0, "test"),
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
            status,
        )

    private suspend fun fixture(block: suspend (SnapshotCaptureRepository) -> Unit) {
        check(Db.snapshotDao.query().first().isEmpty())
        try {
            block(SnapshotCaptureRepository)
        } finally {
            Db.snapshotDao.query().first().forEach { SnapshotStore.delete(it) }
        }
    }
}
