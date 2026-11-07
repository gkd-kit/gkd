package li.gkd.app.subscription

import kotlinx.serialization.json.jsonObject
import li.gkd.app.subscription.RawSubscription.RawApp
import li.gkd.app.subscription.RawSubscription.RawAppGroup
import li.gkd.app.subscription.SubscriptionJson.toJson5String
import li.songe.json5.Json5
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRuleGroupCopyTest {
    @Test
    fun appImportKeepsGroupsAndTheirScopeLinksAcrossKeyConflicts() {
        val first = group(5, "First")
        val second = group(5, "Second", scopeKeys = listOf(6, 5, 11))
        val linked = group(6, "Linked", scopeKeys = listOf(5))

        val copied = parseCopiedApp(1L to first, 2L to second, 2L to linked)
        assertEquals(
            listOf(
                first,
                second.copy(key = 6, scopeKeys = listOf(7)),
                linked.copy(key = 7, scopeKeys = listOf(6)),
            ),
            copied.groups,
        )

        val existing = listOf(group(6, "Existing conflict"), group(11, "Existing external"))
        val imported = destination(existing).editResult { mergeApp(copied) }.expectValue()

        assertEquals(
            existing +
                listOf(
                    first,
                    second.copy(key = 12, scopeKeys = listOf(7)),
                    linked.copy(key = 7, scopeKeys = listOf(12)),
                ),
            imported.apps.single().groups,
        )
    }

    @Test
    fun appImportKeepsGroupsAndScopeLinksWhenConflictingKeysOverflow() {
        val first = group(Int.MAX_VALUE, "First")
        val second = group(Int.MAX_VALUE, "Second", scopeKeys = listOf(Int.MIN_VALUE))
        val linked = group(Int.MIN_VALUE, "Linked", scopeKeys = listOf(Int.MAX_VALUE))

        val copied = parseCopiedApp(1L to first, 2L to second, 2L to linked)
        val expected =
            listOf(
                first,
                second.copy(key = Int.MIN_VALUE, scopeKeys = listOf(Int.MIN_VALUE + 1)),
                linked.copy(key = Int.MIN_VALUE + 1, scopeKeys = listOf(Int.MIN_VALUE)),
            )

        assertEquals(expected, copied.groups)

        val imported = destination().editResult { mergeApp(copied) }.expectValue()
        assertEquals(expected, imported.apps.single().groups)
    }

    @Test
    fun scopeDoesNotBindToAnotherSubscriptionWhenGroupKeysAreUnique() {
        val first = group(1, "First", scopeKeys = listOf(7))
        val unrelated = group(7, "Unrelated")

        val copied = parseCopiedApp(1L to first, 2L to unrelated)

        assertEquals(listOf(first.copy(scopeKeys = emptyList()), unrelated), copied.groups)
    }

    @Test
    fun singleSubscriptionCopyKeepsScopeToExistingDestinationGroup() {
        val first = group(1, "First", scopeKeys = listOf(7))
        val second = group(2, "Second", scopeKeys = listOf(1))
        val existing = group(7, "Existing")

        val copied = parseCopiedApp(1L to first, 1L to second)
        val imported = destination(listOf(existing)).editResult { mergeApp(copied) }.expectValue()

        assertEquals(listOf(existing, first, second), imported.apps.single().groups)
    }

    @Test
    fun duplicateNamesReachImportValidationEvenWithKeyCollision() {
        val first = group(5, "Duplicate")
        val second = group(5, "Other rule content").copy(name = first.name)

        val copied = parseCopiedApp(1L to first, 2L to second)
        assertEquals(listOf(first, second.copy(key = 6)), copied.groups)

        val result = assertRejected { destination().editResult { mergeApp(copied) } }
        assertEquals(SubscriptionFailureReason.RuleNameDuplicate, result.reason)
    }

    private fun parseCopiedApp(vararg groups: Pair<Long, RawAppGroup>): RawApp {
        val copied = RawApp("app.id", null, prepareAppGroupsForCopy(groups.toList()))
        return parseInput(toJson5String(copied)).parseApp().expectValue()
    }

    private fun destination(groups: List<RawAppGroup> = emptyList()) =
        RawSubscription(
            id = -2,
            name = "Local",
            version = 0,
            apps = listOf(RawApp("app.id", null, groups)),
        )

    private fun group(key: Int, name: String, scopeKeys: List<Int>? = null): RawAppGroup =
        RawSubscriptionParser.parseAppGroup(
            Json5.parseToJsonElement(
                    """{key: $key, name: '$name', scopeKeys: $scopeKeys, rules: [{matches: '[text="$name"]'}]}""",
                )
                .jsonObject,
        )
}
