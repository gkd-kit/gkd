package li.gkd.app.external

import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import li.gkd.app.storage.appStorage
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Runs once inside the isolated application's lifecycle, using its real private storage. */
suspend fun assertExternalCalls() = coroutineScope {
    val file = appStorage().privateStore.resolve("external-call-token.txt")
    assertFalse(file.exists())
    assertFalse(ExternalCalls.accepts(null))
    assertFalse(ExternalCalls.accepts(""))
    assertFalse(ExternalCalls.accepts("attacker"))
    assertFalse(file.exists())

    // Concurrent first reads must share the same persisted credential.
    val tokens = List(16) { async(Dispatchers.Default) { ExternalCalls.token() } }.awaitAll()
    assertEquals(1, tokens.toSet().size)
    val old = tokens.first()
    assertTrue(old.matches(Regex("[A-Za-z0-9_-]{16}")))
    assertEquals(old, file.readText())
    assertTrue(ExternalCalls.accepts(old))
    assertFalse(ExternalCalls.accepts(old + "x"))

    // A failed replacement must leave the existing credential usable, then allow retry.
    val blocked = file.parentFile.resolve("${file.name}.tmp")
    assertTrue(blocked.mkdir())
    try {
        assertFailsWith<IOException> { ExternalCalls.resetToken() }
        assertTrue(ExternalCalls.accepts(old))
        assertEquals(old, file.readText())
    } finally {
        blocked.delete()
    }
    val fresh = ExternalCalls.resetToken()
    assertNotEquals(old, fresh)
    assertTrue(fresh.matches(Regex("[A-Za-z0-9_-]{16}")))
    assertEquals(fresh, file.readText())
    assertFalse(ExternalCalls.accepts(old))
    assertTrue(ExternalCalls.accepts(fresh))

    // Loading persisted legacy credentials must not silently rotate them.
    val legacy = "ab".repeat(32)
    file.writeText(legacy)
    assertEquals(legacy, ExternalCalls.token())
    assertTrue(ExternalCalls.accepts(legacy))
    val replacement = ExternalCalls.resetToken()
    assertTrue(replacement.matches(Regex("[A-Za-z0-9_-]{16}")))
    assertFalse(ExternalCalls.accepts(legacy))
    assertTrue(ExternalCalls.accepts(replacement))
}
