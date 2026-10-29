package li.gkd.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException
import li.gkd.app.util.JsonUtils
import li.gkd.db.A11yEventLog

class A11yEventLogItemTest {
    @Test
    fun storedJsonArrayPreservesEscapedEventText() {
        val text = listOf("\"quoted\"", "line\nnext", "C:\\path", "中文", "")
        val raw = JsonUtils.default.encodeToString(text)
        val item = eventLog(raw).toLogItem()

        assertEquals(text, item.parsedText.getOrThrow())
        assertEquals(raw, item.eventLog.text)
    }

    @Test
    fun legacyEmptyStringAndEmptyArrayBothRepresentNoEventText() {
        for (raw in listOf("", "[]")) {
            val item = eventLog(raw).toLogItem()
            assertEquals(emptyList(), item.parsedText.getOrThrow())
            assertEquals(raw, item.eventLog.text)
        }
    }

    @Test
    fun malformedRecordKeepsRawTextAndErrorWithoutPreventingNextRecordParsing() {
        for (raw in listOf("{broken", "{}", "null", "[null]")) {
            val item = eventLog(raw).toLogItem()
            assertEquals(raw, item.eventLog.text)
            val error = assertIs<SerializationException>(item.parsedText.exceptionOrNull())
            assertTrue(error.message.orEmpty().isNotBlank())
        }
        assertEquals(
            listOf("valid"),
            eventLog("""["valid"]""").toLogItem().parsedText.getOrThrow(),
        )
    }

    private fun eventLog(text: String) =
        A11yEventLog(
            id = 1,
            ctime = 1,
            type = 0x20,
            appId = "sample.app",
            name = "sample.Activity",
            desc = null,
            text = text,
        )
}
