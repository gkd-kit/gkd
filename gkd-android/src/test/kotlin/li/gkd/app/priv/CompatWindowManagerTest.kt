package li.gkd.app.priv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatWindowManagerTest {
    @Test
    fun namedSecureFlagIsDetectedForFocusedWindow() {
        val dump = windowDump(
            focusedWindowFlags = "LAYOUT_IN_SCREEN|SECURE|DRAWS_SYSTEM_BAR_BACKGROUNDS",
        )

        assertTrue(parseFocusedWindowSecure(dump, TARGET_APP_ID) == true)
    }

    @Test
    fun namedFlagsWithoutSecureAreNotProtected() {
        val dump = windowDump(
            focusedWindowFlags = "LAYOUT_IN_SCREEN|DRAWS_SYSTEM_BAR_BACKGROUNDS",
        )

        assertFalse(parseFocusedWindowSecure(dump, TARGET_APP_ID) ?: true)
    }

    @Test
    fun legacyHexFlagsAreDecoded() {
        assertTrue(
            parseFocusedWindowSecure(
                windowDump(focusedWindowFlags = "#81812100"),
                TARGET_APP_ID,
            ) == true,
        )
        assertFalse(
            parseFocusedWindowSecure(
                windowDump(focusedWindowFlags = "#81810100"),
                TARGET_APP_ID,
            ) ?: true,
        )
    }

    @Test
    fun secureFlagOnAnotherWindowIsIgnored() {
        val dump = windowDump(
            focusedWindowFlags = "LAYOUT_IN_SCREEN",
            otherWindowFlags = "SECURE",
        )

        assertEquals(false, parseFocusedWindowSecure(dump, TARGET_APP_ID))
    }

    @Test
    fun focusChangeAndTruncatedDumpAreUnknown() {
        val dump = windowDump(focusedWindowFlags = "SECURE")

        assertNull(parseFocusedWindowSecure(dump, "other.app"))
        assertNull(
            parseFocusedWindowSecure(
                "mCurrentFocus=Window{abc123 u0 $TARGET_APP_ID/.MainActivity}",
                TARGET_APP_ID,
            ),
        )
    }

    private fun windowDump(
        focusedWindowFlags: String,
        otherWindowFlags: String = "LAYOUT_IN_SCREEN",
    ) = """
        WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
          mCurrentFocus=Window{abc123 u0 $TARGET_APP_ID/.MainActivity}
          Window #0 Window{def456 u0 other.app/.OtherActivity}:
            mAttrs={(0,0)(fillxfill)
              fl=$otherWindowFlags
              pfl=NO_MOVE_ANIMATION}
          Window #1 Window{abc123 u0 $TARGET_APP_ID/.MainActivity}:
            mAttrs={(0,0)(fillxfill)
              fl=$focusedWindowFlags
              pfl=NO_MOVE_ANIMATION}
    """.trimIndent()

    private companion object {
        const val TARGET_APP_ID = "target.app"
    }
}
