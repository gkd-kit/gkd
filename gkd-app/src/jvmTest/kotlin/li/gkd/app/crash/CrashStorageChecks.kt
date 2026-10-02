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
