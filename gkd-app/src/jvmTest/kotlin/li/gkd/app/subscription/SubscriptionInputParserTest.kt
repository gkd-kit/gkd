package li.gkd.app.subscription

import li.gkd.app.error.ErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionInputParserTest {

    @Test
    fun singleGroupGetsDefaultKey() {
        val input =
            parseInput(
                source =
                    """
                    {
                      name: 'Rule',
                      rules: [],
                    }
                    """
                        .trimIndent(),
                defaultGroupKey = 7,
            )

        val appGroup = input.parseAppGroup("app.id").expectValue()
        val globalGroup = input.parseGlobalGroup().expectValue()

        assertEquals(7, appGroup.key)
        assertEquals(7, globalGroup.key)
    }

    @Test
    fun fullAppGetsUniqueKeysWithoutDroppingGroups() {
        val input =
            parseInput(
                source =
                    """
                    {
                      id: 'app.id',
                      groups: [
                        { name: 'First', rules: [] },
                        { key: 2, name: 'Explicit', rules: [] },
                        { name: 'Second', rules: [] },
                      ],
                    }
                    """
                        .trimIndent(),
            )

        val groups = input.parseAppGroups("app.id").expectValue()

        assertEquals(listOf("First", "Explicit", "Second"), groups.map { it.name })
        assertEquals(listOf(0, 2, 1), groups.map { it.key })
    }

    @Test
    fun quotedNumericKeyIsReservedWhenFillingMissingKeys() {
        val input =
            parseInput(
                source =
                    """
                    {
                      id: 'app.id',
                      groups: [
                        { key: '0', name: 'Quoted', rules: [] },
                        { name: 'Filled', rules: [] },
                      ],
                    }
                    """
                        .trimIndent(),
            )

        val groups = input.parseAppGroups("app.id").expectValue()

        assertEquals(listOf("Quoted", "Filled"), groups.map { it.name })
        assertEquals(listOf(0, 1), groups.map { it.key })
    }

    @Test
    fun appGroupInputAcceptsFullAppAndSingleGroup() {
        val fullApp =
            parseInput(
                source =
                    """
                    {
                      id: 'app.id',
                      groups: [{ key: 3, name: 'Wrapped', rules: [] }],
                    }
                    """
                        .trimIndent(),
            )
        val singleGroup =
            parseInput(
                source = "{ key: 4, name: 'Single', rules: [] }",
            )

        assertEquals("Wrapped", fullApp.parseAppGroup("app.id").expectValue().name)
        assertEquals("Single", singleGroup.parseAppGroup("app.id").expectValue().name)
    }

    @Test
    fun fullAppMustMatchExpectedAppId() {
        val input =
            parseInput(
                source =
                    """
                    {
                      id: 'other.app',
                      groups: [{ key: 0, name: 'Rule', rules: [] }],
                    }
                    """
                        .trimIndent(),
            )

        val error = assertRejected {
            input.parseAppGroups("app.id")
        }

        assertEquals(SubscriptionFailureReason.RuleAppIdMismatch, error.reason)
    }

    @Test
    fun appInputRequiresAtLeastOneGroup() {
        val input =
            parseInput(
                source = "{ id: 'app.id', groups: [] }",
            )

        val error = assertRejected {
            input.parseApp()
        }

        assertEquals(SubscriptionFailureReason.RuleInputRequired, error.reason)
    }

    @Test
    fun reportsSyntaxAndTopLevelTypeErrors() {
        val syntaxError = assertRejected {
            SubscriptionInputParser.parse(source = "{")
        }
        val typeError = assertRejected {
            SubscriptionInputParser.parse(source = "[]")
        }

        assertEquals(SubscriptionFailureReason.FormatInvalidDetail, syntaxError.reason)
        assertTrue((syntaxError.errorMessage as ErrorMessage.Resource).arguments.isNotEmpty())
        assertEquals(SubscriptionFailureReason.RuleObjectRequired, typeError.reason)
    }
}
