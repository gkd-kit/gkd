package li.songe.gkd.priv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatAccessibilityManagerTest {

    @Test
    fun androidOCurrentUserAutomationIsRunning() {
        val dump = """
            User state[attributes:{id=0, currentUser=true, accessibilityEnabled=false, Service[label=UiAutomation]
                       services:{}
        """.trimIndent()

        assertTrue(containsLegacyUiAutomation(dump))
    }

    @Test
    fun androidOOrdinaryAccessibilityServiceIsNotAutomation() {
        val dump = """
            User state[attributes:{id=0, currentUser=true, accessibilityEnabled=true}
                       services:{Service[label=TalkBack]}
        """.trimIndent()

        assertFalse(containsLegacyUiAutomation(dump))
    }

    @Test
    fun androidONonCurrentUserAutomationIsIgnored() {
        val dump = """
            User state[attributes:{id=0, currentUser=false, Service[label=UiAutomation]
                       services:{}
            User state[attributes:{id=10, currentUser=true}
                       services:{}
        """.trimIndent()

        assertFalse(containsLegacyUiAutomation(dump))
    }

    @Test
    fun androidOFindsAutomationInSecondCurrentUserBlock() {
        val dump = """
            User state[attributes:{id=0, currentUser=false}
                       services:{}
            User state[attributes:{id=10, currentUser=true, Service[label=UiAutomation]
                       services:{}
        """.trimIndent()

        assertTrue(containsLegacyUiAutomation(dump))
    }

    @Test
    fun androidPUiAutomationInUserAttributesIsRunning() {
        val dump = """
            User state[attributes:{id=0, currentUser=true, Ui Automation[eventTypes=TYPES_ALL_MASK]
                       services:{}
        """.trimIndent()

        assertTrue(containsModernUiAutomation(dump))
    }

    @Test
    fun modernStandaloneUiAutomationIsRunning() {
        val dump = """
            ACCESSIBILITY MANAGER (dumpsys accessibility)
              Ui Automation[eventTypes=TYPES_ALL_MASK, notificationTimeout=0]
        """.trimIndent()

        assertTrue(containsModernUiAutomation(dump))
    }

    @Test
    fun uiAutomationMarkerDoesNotRequireLineBoundary() {
        val dump = "state=Ui Automation[eventTypes=TYPES_ALL_MASK]"

        assertTrue(containsModernUiAutomation(dump))
    }

    @Test
    fun emptyOrTruncatedDumpIsNotAutomation() {
        assertFalse(containsLegacyUiAutomation(""))
        assertFalse(
            containsLegacyUiAutomation(
                "User state[attributes:{id=0, currentUser=true, Service[",
            ),
        )
    }
}
