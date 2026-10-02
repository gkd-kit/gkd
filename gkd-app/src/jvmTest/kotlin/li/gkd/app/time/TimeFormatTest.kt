package li.gkd.app.time

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
    @Test
    fun samePatternUsesCurrentLocaleAfterLanguageChanges() {
        val original = Locale.getDefault()
        try {
            // Mid-month avoids depending on the host timezone at a month boundary.
            val timestamp = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
            Locale.setDefault(Locale.US)
            assertEquals("January", timestamp.format("MMMM"))
            Locale.setDefault(Locale.FRANCE)
            assertEquals("janvier", timestamp.format("MMMM"))
            Locale.setDefault(Locale.US)
            assertEquals("January", timestamp.format("MMMM"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun concurrentLogTimestampsDoNotShareMutableFormatterState() {
        val originalZone = TimeZone.getDefault()
        val executor = Executors.newFixedThreadPool(8)
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val pattern = "yyyy-MM-dd HH:mm:ss.SSS"
            val expected = DateTimeFormatter.ofPattern(pattern).withZone(ZoneOffset.UTC)
            val start = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()
            val timestamps = List(512) { start + it * 123456789L }
            val results = executor.invokeAll(timestamps.map { timestamp ->
                Callable { timestamp.format(pattern) }
            }).map { it.get() }
            assertEquals(timestamps.map { expected.format(Instant.ofEpochMilli(it)) }, results)
        } finally {
            executor.shutdownNow()
            TimeZone.setDefault(originalZone)
        }
    }
}
