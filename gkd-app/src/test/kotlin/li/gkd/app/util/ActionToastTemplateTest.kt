package li.gkd.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionToastTemplateTest {
    @Test
    fun repeatedVariablesAndMultilineTextRenderForPreviewAndActualToast() {
        assertEquals(
            "子规则 / 规则组\n2147483648 · 子规则",
            ActionToastTemplate.render($$"${1} / ${2}\n${3} · ${1}", "子规则", "规则组", 2147483648L),
        )
    }

    @Test
    fun plainTextAndUnknownVariablesArePreserved() {
        assertEquals(
            $$"GKD ${unknown}",
            ActionToastTemplate.render($$"GKD ${unknown}", "子规则", "规则组", 3L),
        )
    }

}
