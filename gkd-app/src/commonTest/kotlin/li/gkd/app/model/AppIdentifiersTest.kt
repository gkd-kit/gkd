package li.gkd.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppIdentifiersTest {
    @Test
    fun appIdsAcceptSingleSegmentsAndKeepCharacterAndSegmentChecks() {
        for (id in listOf("android", "platform", "App_1", "a", "com.example.app", "a.B_2")) {
            assertTrue(id.isValidAppId(), id)
        }
        for (id in
            listOf(
                "",
                ".",
                ".app",
                "app.",
                "app..name",
                "1app",
                "_app",
                "app.1name",
                "app._name",
                "app-name",
                "app name",
                "app/name",
                "应用",
                "app\n",
            )) {
            assertFalse(id.isValidAppId(), id)
        }
    }

    @Test
    fun activityIdsRejectEmptySegmentsIncludingTrailingDot() {
        for (id in listOf("Main", ".Main", "com.example.Main", "com.example.Main\$Inner", "a._B")) {
            assertTrue(id.isValidActivityId(), id)
        }
        for (id in listOf("", ".", "com.example.", "com..Main", "com.Main.", "com/Main", "a b")) {
            assertFalse(id.isValidActivityId(), id)
        }
        assertEquals(
            ExcludeData(emptyMap(), setOf("android" to "com.example.Main")),
            ExcludeData.parse("android/com.example.Main\nandroid/com.example.\nandroid/."),
        )
    }

    @Test
    fun persistedExclusionsRoundTripSingleSegmentIdsAndIgnoreMalformedEntries() {
        val value =
            ExcludeData(
                appIds = mapOf("android" to true, "platform" to false, "com.example.app" to true),
                activityIds = setOf("android" to ".Main", "platform" to "AppActivity"),
            )
        assertEquals(value, ExcludeData.parse(value.stringify()))
        assertEquals(value, ExcludeData.parse(value.stringify() + "\n!\n/Main\nbad-id\napp./Main"))
        assertEquals(
            ExcludeData(emptyMap(), setOf("android" to ".Main")),
            ExcludeData.parse(".Main", "android"),
        )
    }
}
