package li.gkd.app.storage

import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StartupLogsTest {
    @Test
    fun writesLocalSecondFilenameAndRetainsLatestThirtyStarts() {
        val root = Files.createTempDirectory("gkd-startup-log").toFile()
        try {
            val directory = root.resolve("private/startup-log")
            val time = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).parse("20261006162246")!!.time
            StartupLogs.write(directory, time, "{\"reason\":\"external_available\"}")
            val first = directory.resolve("gkd-startup-20261006162246.json")
            assertEquals("{\"reason\":\"external_available\"}", first.readText())
            first.setLastModified(1)
            val oldFile = directory.resolve("old-startup.txt").apply {
                writeText("old")
                setLastModified(2)
            }
            repeat(30) { index ->
                StartupLogs.write(directory, time + (index + 1) * 1000L, "{\"index\":$index}")
            }
            assertFalse(first.exists())
            assertEquals(30, directory.listFiles()!!.count { it.isFile })
            assertFalse(oldFile.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedWriteDoesNotPruneExistingHistory() {
        val root = Files.createTempDirectory("gkd-startup-log-failure").toFile()
        try {
            val time = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).parse("20261006162246")!!.time
            val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT)
            val previous = (1..31).associate { index ->
                val name = "gkd-startup-" + formatter.format(java.util.Date(time - index * 1000L)) + ".json"
                root.resolve(name).apply { writeText("previous-$index") } to "previous-$index"
            }
            root.resolve("gkd-startup-20261006162246.json").mkdir()
            assertFailsWith<java.io.IOException> { StartupLogs.write(root, time, "new") }
            previous.forEach { (file, text) -> assertEquals(text, file.readText()) }
        } finally {
            root.deleteRecursively()
        }
    }
}
