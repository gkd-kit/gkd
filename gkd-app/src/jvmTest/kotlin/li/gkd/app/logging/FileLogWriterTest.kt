package li.gkd.app.logging

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import li.gkd.app.testing.TestFiles
import li.gkd.app.time.format

class FileLogWriterTest {
    private val metadata = LogMetadata("Test", "1", "device", "app (1)")

    @Test
    fun concurrentEntriesStayWholeAndCloseDrainsAllWrites() = fixture { root ->
        val writer = FileLogWriter(root, metadata)
        val workers = Executors.newFixedThreadPool(4)
        val time = System.currentTimeMillis()
        try {
            (0 until 100)
                .map { index ->
                    workers.submit {
                        writer.append(
                            "tag",
                            "caller-$index",
                            "origin-$index",
                            listOf("first-$index", "last-$index"),
                            time,
                        )
                    }
                }
                .forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            workers.shutdownNow()
            writer.close()
        }
        val text = root.listFiles()!!.single().readText()
        assertEquals(2, Regex("=== Log ===").findAll(text).count())
        for (index in 0 until 100) {
            assertEquals(
                1,
                Regex(
                        Regex.escape(
                            "tag, caller-$index, origin-$index\n[0]: first-$index\n[1]: last-$index\n\n",
                        ),
                    )
                    .findAll(
                        text,
                    )
                    .count(),
            )
        }
    }

    @Test
    fun fileIoRunsInBackground() = fixture { root ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var ioThread = ""
        val directory =
            object : File(root.path) {
                override fun isDirectory(): Boolean {
                    ioThread = Thread.currentThread().name
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return super.isDirectory()
                }
            }
        val writer = FileLogWriter(directory, metadata)
        val caller = Thread.currentThread().name
        try {
            writer.append("tag", caller, "location", listOf("message"), System.currentTimeMillis())
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals("gkd-log-writer", ioThread)
        } finally {
            release.countDown()
            writer.close()
        }
        val text = root.listFiles()!!.single().readText()
        assertTrue(text.contains(", $caller, "))
        assertTrue(text.contains("\nmessage\n\n"))
    }

    @Test
    fun failedWriteIsReportedWithoutReplayAndWriterCanRecover() = fixture { root ->
        val directory = root.resolve("log").apply { writeText("blocked") }
        FileLogWriter(directory, metadata).use { writer ->
            writer.append("tag", "thread", "loc", listOf("lost"), System.currentTimeMillis())
            assertFailsWith<IOException> { writer.flush() }
            assertTrue(directory.delete())
            writer.append("tag", "thread", "loc", listOf("recovered"), System.currentTimeMillis())
            writer.flush()
            val text = directory.listFiles()!!.single().readText()
            assertTrue(text.contains("\nrecovered\n"))
            assertFalse(text.contains("\nlost\n"))
        }
    }

    @Test
    fun newDailyFilePreservesHeaderAndExistingAgeBoundary() = fixture { root ->
        val time = System.currentTimeMillis()
        val old =
            (1..5).map {
                root.resolve("gkd-2000010$it.log").apply {
                    writeText("old")
                    assertTrue(setLastModified(time - 8.days.inWholeMilliseconds))
                }
            }
        val boundary =
            root.resolve("gkd-20000106.log").apply {
                writeText("boundary")
                assertTrue(setLastModified(time - 7.days.inWholeMilliseconds))
            }
        val recent = root.resolve("gkd-20000107.log").apply { writeText("recent") }
        FileLogWriter(root, metadata).use { writer ->
            writer.append("tag", "thread", "loc", listOf("message"), time)
        }
        assertTrue(old.none { it.exists() })
        assertTrue(boundary.isFile)
        assertTrue(recent.isFile)
        val file = root.resolve("gkd-${time.format("yyyyMMdd")}.log")
        assertTrue(file.readText().contains("Test: 1\nDevice: device\nApp: app (1)\n"))
    }

    @Test
    fun queueBoundsPreferErrorsAndFlushIncludesLossSummary() = fixture { root ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val directory =
            object : File(root.path) {
                override fun isDirectory(): Boolean {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return super.isDirectory()
                }
            }
        val limits =
            LogLimits(
                queueEntries = 2,
                queueBytes = 512,
                entryBytes = 256,
                fileBytes = 1024,
                totalBytes = 8192,
            )
        val writer = FileLogWriter(directory, metadata, limits)
        val now = System.currentTimeMillis()
        try {
            writer.append("test", "caller", "loc", listOf("in-flight"), now)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            writer.append("test", "caller", "loc", listOf("discard-debug"), now, LogLevel.DEBUG)
            writer.append("test", "caller", "loc", listOf("keep-info"), now, LogLevel.INFO)
            writer.append("test", "caller", "loc", listOf("keep-error"), now, LogLevel.ERROR)
            writer.append("test", "caller", "loc", listOf("discard-incoming"), now, LogLevel.DEBUG)
            assertEquals(2, writer.status().queuedEntries)
            assertTrue(writer.status().queuedBytes <= 512)
            assertEquals(2, writer.status().dropped)
            release.countDown()
            writer.flush()
            val text = root.listFiles()!!.joinToString("\n") { it.readText() }
            assertTrue(text.contains("keep-error"))
            assertTrue(text.contains("keep-info"))
            assertFalse(text.contains("discard-"))
            assertTrue(text.contains("log_queue_dropped=2"))
        } finally {
            release.countDown()
            writer.close()
        }
        assertFailsWith<IllegalStateException> {
            writer.append("test", "caller", "loc", listOf("late"), now)
        }
    }

    @Test
    fun byteBudgetAppliesBeforeEntryCountAndUtf8TruncationIsBounded() = fixture { root ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val directory =
            object : File(root.path) {
                override fun isDirectory(): Boolean {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return super.isDirectory()
                }
            }
        val writer =
            FileLogWriter(
                directory,
                metadata,
                LogLimits(
                    queueEntries = 10,
                    queueBytes = 512,
                    entryBytes = 256,
                    fileBytes = 1024,
                    totalBytes = 8192,
                ),
            )
        val now = System.currentTimeMillis()
        try {
            writer.append("test", "caller", "loc", listOf("in-flight"), now)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repeat(4) { writer.append("test", "caller", "loc", listOf("界😀".repeat(100)), now) }
            assertEquals(2, writer.status().queuedEntries)
            assertEquals(2, writer.status().dropped)
            assertEquals(4, writer.status().truncated)
            release.countDown()
            writer.flush()
            assertTrue(root.listFiles()!!.all { it.length() <= 1024 })
            assertTrue(root.listFiles()!!.any { it.readText().contains("log entry truncated") })
            assertTrue(root.listFiles()!!.none { it.readText().contains("\uFFFD") })
        } finally {
            release.countDown()
            writer.close()
        }
    }

    @Test
    fun rollsExistingFilesAndEnforcesTotalCapacityWithoutDeletingUnrelatedData() = fixture { root ->
        val now = System.currentTimeMillis()
        val unrelated =
            root.resolve("other.log").apply {
                writeText("keep")
                setLastModified(1)
            }
        val oldest =
            root.resolve("gkd-20000101.log").apply {
                writeText("expired")
                setLastModified(1)
            }
        val limits =
            LogLimits(
                queueEntries = 100,
                queueBytes = 8192,
                entryBytes = 256,
                fileBytes = 512,
                totalBytes = 2048,
            )
        repeat(2) { pass ->
            FileLogWriter(root, metadata, limits).use { writer ->
                repeat(10) {
                    writer.append(
                        "tag",
                        "thread",
                        "loc",
                        listOf("$pass:$it " + "x".repeat(180)),
                        now,
                    )
                }
            }
            val logs = root.listFiles()!!.filter { it.name.startsWith("gkd-") }
            assertTrue(logs.size > 1)
            assertTrue(logs.all { it.length() <= limits.fileBytes })
            assertTrue(logs.sumOf { it.length() } <= limits.totalBytes)
            assertTrue(logs.any { it.readText().contains("$pass:9 ") })
        }
        assertFalse(oldest.exists())
        assertEquals("keep", unrelated.readText())
    }

    @Test
    fun sharedSnapshotSurvivesFurtherRotationAndOversizedMetadataIsBounded() = fixture { root ->
        val source = root.resolve("log")
        val snapshot = root.resolve("snapshot")
        val limits =
            LogLimits(
                queueEntries = 100,
                queueBytes = 8192,
                entryBytes = 256,
                fileBytes = 512,
                totalBytes = 1024,
            )
        FileLogWriter(source, metadata.copy(device = "device".repeat(1000)), limits).use { writer ->
            val now = System.currentTimeMillis()
            writer.append("tag", "caller", "loc", listOf("before-export"), now)
            writer.copyTo(snapshot)
            val saved = snapshot.listFiles()!!.associate { it.name to it.readText() }
            repeat(20) {
                writer.append("tag", "caller", "loc", listOf("next-$it" + "x".repeat(180)), now)
            }
            writer.flush()
            assertTrue(source.listFiles()!!.all { it.length() <= 512 })
            assertEquals(saved, snapshot.listFiles()!!.associate { it.name to it.readText() })
            assertTrue(saved.values.any { it.contains("before-export") })
        }
    }

    @Test
    fun rejectedLargeWarningDoesNotEvictSmallerAcceptedDebugEntry() = fixture { root ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val directory =
            object : File(root.path) {
                override fun isDirectory(): Boolean {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return super.isDirectory()
                }
            }
        val writer =
            FileLogWriter(
                directory,
                metadata,
                LogLimits(
                    queueEntries = 10,
                    queueBytes = 512,
                    entryBytes = 512,
                    fileBytes = 1024,
                    totalBytes = 8192,
                ),
            )
        val now = System.currentTimeMillis()
        try {
            writer.append("test", "caller", "loc", listOf("in-flight"), now)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            writer.append("test", "caller", "loc", listOf("keep-debug"), now, LogLevel.DEBUG)
            writer.append(
                "test",
                "caller",
                "loc",
                listOf("keep-error " + "x".repeat(240)),
                now,
                LogLevel.ERROR,
            )
            writer.append(
                "test",
                "caller",
                "loc",
                listOf("reject-warning " + "x".repeat(360)),
                now,
                LogLevel.WARN,
            )
            assertEquals(2, writer.status().queuedEntries)
            assertEquals(1L, writer.status().dropped)
            release.countDown()
            writer.flush()
            val text = root.listFiles()!!.joinToString("\n") { it.readText() }
            assertTrue(text.contains("keep-debug"))
            assertTrue(text.contains("keep-error"))
            assertFalse(text.contains("reject-warning"))
        } finally {
            release.countDown()
            writer.close()
        }
    }

    @Test
    fun arabicLocaleStillUsesManagedAsciiFileNamesAndRetention() = fixture { root ->
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            FileLogWriter(
                    root,
                    metadata,
                    LogLimits(
                        queueEntries = 100,
                        queueBytes = 8192,
                        entryBytes = 256,
                        fileBytes = 512,
                        totalBytes = 1024,
                    ),
                )
                .use { writer ->
                    repeat(20) {
                        writer.append(
                            "tag",
                            "caller",
                            "loc",
                            listOf("entry-$it " + "x".repeat(180)),
                            System.currentTimeMillis(),
                        )
                    }
                }
            val files = root.listFiles()!!
            assertTrue(files.all { it.name.matches(Regex("gkd-[0-9]{8}(?:-[0-9]+)?\\.log")) })
            assertTrue(files.sumOf { it.length() } <= 1024)
            assertTrue(files.any { it.readText().contains("entry-19") })
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun flushWaitsForTheBatchAlreadyRemovedFromQueue() = fixture { root ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val directory =
            object : File(root.path) {
                override fun isDirectory(): Boolean {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return super.isDirectory()
                }
            }
        val writer = FileLogWriter(directory, metadata)
        val executor = Executors.newSingleThreadExecutor()
        try {
            writer.append(
                "test",
                "caller",
                "loc",
                listOf("still-writing"),
                System.currentTimeMillis(),
            )
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(0, writer.status().queuedEntries)
            val flushed = executor.submit { writer.flush() }
            assertFailsWith<java.util.concurrent.TimeoutException> {
                flushed.get(100, TimeUnit.MILLISECONDS)
            }
            release.countDown()
            flushed.get(5, TimeUnit.SECONDS)
            assertTrue(root.listFiles()!!.single().readText().contains("still-writing"))
        } finally {
            release.countDown()
            writer.close()
            executor.shutdownNow()
        }
    }

    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory(TestFiles.root.toPath(), "logging-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
