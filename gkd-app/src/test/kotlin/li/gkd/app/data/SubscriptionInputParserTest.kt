package li.gkd.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionInputParserTest {

    @Test
    fun singleGroupGetsDefaultKey() {
        val input = SubscriptionInputParser.parse(
            """
            {
              name: 'Rule',
              rules: [],
            }
            """.trimIndent(),
            defaultGroupKey = 7,
        )

        val appGroup = input.parseAppGroup("app.id")
        val globalGroup = input.parseGlobalGroup()

        assertEquals(7, appGroup.key)
        assertEquals(7, globalGroup.key)
    }

    @Test
    fun fullAppGetsUniqueKeysWithoutDroppingGroups() {
        val input = SubscriptionInputParser.parse(
            """
            {
              id: 'app.id',
              groups: [
                { name: 'First', rules: [] },
                { key: 2, name: 'Explicit', rules: [] },
                { name: 'Second', rules: [] },
              ],
            }
            """.trimIndent(),
        )

        val groups = input.parseAppGroups("app.id")

        assertEquals(listOf("First", "Explicit", "Second"), groups.map { it.name })
        assertEquals(listOf(0, 2, 1), groups.map { it.key })
    }

    @Test
    fun quotedNumericKeyIsReservedWhenFillingMissingKeys() {
        val input = SubscriptionInputParser.parse(
            """
            {
              id: 'app.id',
              groups: [
                { key: '0', name: 'Quoted', rules: [] },
                { name: 'Filled', rules: [] },
              ],
            }
            """.trimIndent(),
        )

        val groups = input.parseAppGroups("app.id")

        assertEquals(listOf("Quoted", "Filled"), groups.map { it.name })
        assertEquals(listOf(0, 1), groups.map { it.key })
    }

    @Test
    fun appGroupInputAcceptsFullAppAndSingleGroup() {
        val fullApp = SubscriptionInputParser.parse(
            """
            {
              id: 'app.id',
              groups: [{ key: 3, name: 'Wrapped', rules: [] }],
            }
            """.trimIndent(),
        )
        val singleGroup = SubscriptionInputParser.parse(
            "{ key: 4, name: 'Single', rules: [] }",
        )

        assertEquals("Wrapped", fullApp.parseAppGroup("app.id").name)
        assertEquals("Single", singleGroup.parseAppGroup("app.id").name)
    }

    @Test
    fun fullAppMustMatchExpectedAppId() {
        val input = SubscriptionInputParser.parse(
            """
            {
              id: 'other.app',
              groups: [{ key: 0, name: 'Rule', rules: [] }],
            }
            """.trimIndent(),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            input.parseAppGroups("app.id")
        }

        assertEquals("非法规则\nid与当前应用不一致", error.message)
    }

    @Test
    fun appInputRequiresAtLeastOneGroup() {
        val input = SubscriptionInputParser.parse("{ id: 'app.id', groups: [] }")

        val error = assertThrows(IllegalStateException::class.java) {
            input.parseApp()
        }

        assertEquals("非法规则\n至少输入一个规则", error.message)
    }

    @Test
    fun reportsSyntaxAndTopLevelTypeErrors() {
        val syntaxError = assertThrows(IllegalStateException::class.java) {
            SubscriptionInputParser.parse("{")
        }
        val typeError = assertThrows(IllegalStateException::class.java) {
            SubscriptionInputParser.parse("[]")
        }

        assertTrue(syntaxError.message.orEmpty().startsWith("非法格式\n"))
        assertEquals("规则应为对象格式", typeError.message)
    }
}
