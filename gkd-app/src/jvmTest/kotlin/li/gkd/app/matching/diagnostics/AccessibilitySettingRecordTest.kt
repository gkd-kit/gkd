package li.gkd.app.matching.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.util.JsonUtils

class AccessibilitySettingRecordTest {
    @Test
    fun compactRecordsOmitNullFieldsEvenInsideFullDiagnosticJson() {
        val baseline =
            AccessibilitySettingRecord(1, 2, "baseline", 0, "accessibility_enabled", "1", "android")
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        diagnostics.stop(session.id)
        diagnostics.finishAccessibilitySettings(session.id, listOf(baseline), 0)
        val encoded = JsonUtils.keepingNulls.encodeToString(baseline)
        val fields = JsonUtils.keepingNulls.parseToJsonElement(encoded).jsonObject.keys
        assertEquals(
            setOf("notifiedAt", "readAt", "trigger", "userId", "name", "value", "sourcePackage"),
            fields,
        )
        assertEquals(
            baseline,
            JsonUtils.keepingNulls.decodeFromString<AccessibilitySettingRecord>(encoded),
        )
        val capture =
            JsonUtils.default.decodeFromString<MatchDiagnosticCapture>(
                encodeDiagnosticCapture(diagnostics.export(session.id)),
            )
        assertEquals(listOf(baseline), capture.accessibilitySettings)
        val failure = AccessibilitySettingRecord(1, 2, "changed", error = "permission denied")
        val failureJson = JsonUtils.keepingNulls.encodeToString(failure)
        assertEquals(
            setOf("notifiedAt", "readAt", "trigger", "error"),
            JsonUtils.keepingNulls.parseToJsonElement(failureJson).jsonObject.keys,
        )
        assertEquals(
            failure,
            JsonUtils.keepingNulls.decodeFromString<AccessibilitySettingRecord>(failureJson),
        )
    }

    @Test
    fun skipsIdenticalSnapshotsButPreservesValueAndSourceChanges() {
        val changes = AccessibilitySettingChanges()
        val baseline =
            AccessibilitySettingRecord(
                1,
                2,
                "baseline",
                0,
                "accessibility_enabled",
                "1",
                "android",
            )
        assertEquals(baseline, changes.accept(baseline))
        assertNull(
            changes.accept(
                baseline.copy(notifiedAt = 3, readAt = 4, trigger = "changed"),
            ),
        )
        val sourceChange =
            assertNotNull(
                changes.accept(baseline.copy(readAt = 5, sourcePackage = "com.android.shell")),
            )
        assertEquals("1", sourceChange.previousValue)
        assertEquals("android", sourceChange.previousSourcePackage)
        val valueChange =
            assertNotNull(
                changes.accept(
                    baseline.copy(readAt = 6, value = "0", sourcePackage = "com.android.shell"),
                ),
            )
        assertEquals("1", valueChange.previousValue)
        assertEquals("com.android.shell", valueChange.previousSourcePackage)
    }

    @Test
    fun failuresDoNotReplaceBaselineAndUsersAndKeysRemainIndependent() {
        val changes = AccessibilitySettingChanges()
        val baseline =
            AccessibilitySettingRecord(
                1,
                2,
                "baseline",
                0,
                "accessibility_enabled",
                "1",
                null,
            )
        changes.accept(baseline)
        val failure = baseline.copy(readAt = 3, value = null, error = "permission denied")
        assertEquals(failure, changes.accept(failure))
        val changed = assertNotNull(changes.accept(baseline.copy(readAt = 4, value = "0")))
        assertEquals("1", changed.previousValue)
        assertNull(changed.previousSourcePackage)
        assertNull(assertNotNull(changes.accept(baseline.copy(userId = 10))).previousValue)
        assertNull(
            assertNotNull(changes.accept(baseline.copy(name = "enabled_accessibility_services")))
                .previousValue,
        )
        assertNull(assertNotNull(AccessibilitySettingChanges().accept(baseline)).previousValue)
    }

    @Test
    fun extractsSecureSettingsWithUserAndSourceWithoutMixingOtherTables() {
        val records =
            parseAccessibilitySettingsDump(
                sequenceOf(
                    "GLOBAL SETTINGS (user 0)",
                    "_id:1 name:accessibility_enabled pkg:wrong value:0",
                    "SECURE SETTINGS (user 0)",
                    "_id:2 name:accessibility_enabled pkg:android value:1 default:0 defaultSystemSet:true",
                    "_id:3 name:enabled_accessibility_services pkg:com.android.shell value:a/.Service:b/.Other",
                    "SECURE SETTINGS (user 10)",
                    "_id:4 name:accessibility_enabled value:0",
                    "SYSTEM SETTINGS (user 10)",
                    "_id:5 name:accessibility_enabled pkg:wrong value:1",
                ),
                10,
                20,
                "baseline",
            )
        assertEquals(listOf(0, 0, 10), records.map { it.userId })
        assertEquals(listOf("1", "a/.Service:b/.Other", "0"), records.map { it.value })
        assertEquals(listOf("android", "com.android.shell", null), records.map { it.sourcePackage })
        assertEquals(listOf(10L, 10L, 10L), records.map { it.notifiedAt })
    }

    @Test
    fun missingAndDeniedDumpsAreFailuresNotEmptySuccess() {
        assertFailsWith<SecurityException> {
            parseAccessibilitySettingsDump(
                sequenceOf("Permission Denial: missing DUMP"),
                0,
                0,
                "baseline",
            )
        }
        assertFailsWith<IllegalStateException> {
            parseAccessibilitySettingsDump(sequenceOf("Unknown vendor output"), 0, 0, "baseline")
        }
    }

    @Test
    fun finishedSettingsRemainInOriginalSessionAfterReplacementAndLateOperation() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        val operation = diagnostics.beginOperation("NodeRead:Root", 1)!!
        diagnostics.stop(session.id)
        val next = diagnostics.start(MatchTimingPolicy())
        val record =
            AccessibilitySettingRecord(
                1,
                2,
                "baseline",
                0,
                "accessibility_enabled",
                "1",
                "android",
            )
        diagnostics.finishAccessibilitySettings(session.id, listOf(record), 3)
        diagnostics.endOperation(operation, "returned")
        assertEquals(listOf(record), diagnostics.export(session.id).accessibilitySettings)
        assertEquals(3L, diagnostics.export(session.id).droppedAccessibilitySettings)
        assertEquals(emptyList(), diagnostics.export(next.id).accessibilitySettings)
    }
}
