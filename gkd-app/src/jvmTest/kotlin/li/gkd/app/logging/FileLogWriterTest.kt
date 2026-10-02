package li.gkd.app.logging

import li.gkd.app.testing.TestFiles
import li.gkd.app.time.format
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

class FileLogWriterTest {
    private val metadata = LogMetadata("Test", "1", "device", "app (1)")

    @Test
    fun concurrentEntriesStayWholeAndCloseDrainsAllWrites() = fixture { root ->
        val writer = FileLogWriter(root, metadata)
        val workers = Executors.newFixedThreadPool(4)
        val time = System.currentTimeMillis()
        try {
            (0 until 100).map { index ->
                workers.submit {
                    writer.append(
                        "tag",
                        "caller-$index",
                        "origin-$index",
                        listOf("first-$index", "last-$index"),
                        time
                    )
                }
            }.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            workers.shutdownNow()
            writer.close()
        }
        val text = root.listFiles()!!.single().readText()
        assertEquals(2, Regex("=== Log ===").findAll(text).count())
        for (index in 0 until 100) {
            assertEquals(
                1,
                Regex(Regex.escape("tag, caller-$index, origin-$index\n[0]: first-$index\n[1]: last-$index\n\n")).findAll(
                    text
                ).count()
            )
        }
    }

    @Test
    fun fileIoRunsInBackground() = fixture { root ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var ioThread = ""
        val directory = object : File(root.path) {
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
        val old = (1..5).map {
            root.resolve("old-$it.log").apply {
                writeText("old"); assertTrue(setLastModified(time - 8.days.inWholeMilliseconds))
            }
        }
        val boundary = root.resolve("boundary.log").apply {
            writeText("boundary"); assertTrue(setLastModified(time - 7.days.inWholeMilliseconds))
        }
        val recent = root.resolve("recent.log").apply { writeText("recent") }
        FileLogWriter(root, metadata).use { writer ->
            writer.append("tag", "thread", "loc", listOf("message"), time)
        }
        assertTrue(old.none { it.exists() })
        assertTrue(boundary.isFile)
        assertTrue(recent.isFile)
        val file = root.resolve("gkd-${time.format("yyyyMMdd")}.log")
        assertTrue(file.readText().contains("Test: 1\nDevice: device\nApp: app (1)\n"))
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
