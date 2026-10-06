package li.gkd.app.crash

import li.gkd.app.storage.appStorage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun assertCrashReports() {
    val record = CrashData(1, 1, "test", 33, "13", 1, "1", "TestCrash", "fixture", "main", "trace")
    val blocked = appStorage().crashTemp.resolve(record.filename)
    try {
        FileCrashStorage.save(record)
        assertEquals(listOf(record), FileCrashStorage.load())
        assertTrue(FileCrashStorage.deleteAll())
        assertTrue(FileCrashStorage.load().isEmpty())

        // Previously persisted filenames remain deletable after naming changes.
        for (legacyName in listOf("gkd_crash-19700101_080001.json", "gkd_crash-19700101080001.json")) {
            FileCrashStorage.save(record)
            val folders = listOf(appStorage().crash, appStorage().crashTemp)
            folders.forEach { folder ->
                java.nio.file.Files.move(
                    folder.resolve(record.filename).toPath(), folder.resolve(legacyName).toPath(),
                )
            }
            val loaded = FileCrashStorage.load().single()
            assertEquals(record.id, loaded.id)
            assertEquals(legacyName, loaded.filename)
            assertTrue(FileCrashStorage.delete(loaded))
            folders.forEach { assertFalse(it.resolve(legacyName).exists()) }
            assertTrue(FileCrashStorage.load().isEmpty())
        }

        // A real nonempty directory blocks deletion; repairing it permits retry.
        FileCrashStorage.save(record)
        assertTrue(blocked.delete())
        assertTrue(blocked.mkdir())
        val child = blocked.resolve("blocked").apply { writeText("fixture") }
        assertFalse(FileCrashStorage.delete(record))
        assertTrue(child.isFile)
        assertTrue(child.delete())
        assertTrue(FileCrashStorage.delete(record))
        assertFalse(blocked.exists())
        assertTrue(FileCrashStorage.load().isEmpty())
    } finally {
        if (blocked.isDirectory) blocked.deleteRecursively()
        FileCrashStorage.deleteAll()
    }
}
