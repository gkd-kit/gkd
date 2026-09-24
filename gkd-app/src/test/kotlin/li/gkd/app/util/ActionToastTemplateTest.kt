package li.gkd.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionToastTemplateTest {
    @Test
    fun repeatedVariablesAndMultilineTextRenderForPreviewAndActualToast() {
        assertEquals(
            "Sub-rule / Rule Group\n2147483648 · Sub-rule",
            ActionToastTemplate.render($$"${1} / ${2}\n${3} · ${1}", "Sub-rule", "Rule Group", 2147483648L),
        )
    }

    @Test
    fun plainTextAndUnknownVariablesArePreserved() {
        assertEquals(
            $$"GKD ${unknown}",
            ActionToastTemplate.render($$"GKD ${unknown}", "Sub-rule", "Rule Group", 3L),
        )
    }

}
