package li.gkd.app.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogFieldsTest {
    @Test
    fun urlsOmitSecretsButKeepEndpointAndPort() {
        assertEquals(
            "https://example.com:8443/path",
            logUrl("https://user:password@example.com:8443/path?token=secret#fragment"),
        )
        assertEquals("/api/test", logUrl("/api/test?token=secret"))
        assertEquals("data:[omitted]", logUrl("data:text/plain,secret"))
        assertEquals("[invalid URL]", logUrl("not a valid uri"))
    }

    @Test
    fun nodeTextRemainsUsefulAndOversizedFieldsAreExplicit() {
        assertEquals("跳过", logText("跳过"))
        assertEquals("a…[truncated, length=4]", logText("a😀b", 2))
        assertTrue(logText("abc".repeat(300)).endsWith("[truncated, length=900]"))
    }
}
