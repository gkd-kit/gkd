package li.gkd.app.subscription

import li.gkd.app.model.AppInfo
import li.gkd.app.rule.RuleGroupPolicy
import li.gkd.app.rule.RuleRestriction
import li.gkd.app.rule.RuleScopePolicy
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.*
import org.junit.Test

class VersionPatternValidationTest {
    private val info = AppInfo("app.id", "App", 10, "1.0", false, 1, false, 0)

    @Test
    fun missingPatternsStayUnrestrictedButInvalidPatternsNeverMatch() {
        val missing = RawSubscription.StringMatcher(null, null, null)
        assertNull(missing.patternError)
        assertTrue(missing.match("1.0"))
        assertFalse(missing.match(null))
        val invalid = missing.copy(pattern = "[")
        assertNotNull(invalid.patternError)
        assertFalse(invalid.match("1.0"))
        assertFalse(invalid.match(""))
        // Include/exclude still narrow a successfully compiled pattern, without overriding it.
        val restricted =
            missing.copy(
                pattern = "1[.].*",
                include = listOf("1.0", "1.1", "2.0"),
                exclude = listOf("1.1"),
            )
        assertTrue(restricted.match("1.0"))
        assertFalse(restricted.match("1.1"))
        assertFalse(restricted.match("1.2"))
        assertFalse(restricted.match("2.0"))
        assertTrue(invalid.copy(pattern = "1[.].*").match("1.0"))
    }

    /**
     * Invalid inherited conditions and invalid overrides use the card/enable/edit validation path.
     */
    @Test
    fun appGroupAndRulePatternsBlockExecutionAndEveryAppEditorWithTheSameField() {
        val bodies =
            listOf(
                "versionName:{pattern:'['},rules:[{matches:'*'}]" to "versionName.pattern",
                "versionName:{pattern:'['},rules:[{matches:'*',versionName:{pattern:'1.*'}}]" to
                    "versionName.pattern",
                "versionName:{pattern:'1.*'},rules:[{matches:'*',versionName:{pattern:'['}}]" to
                    "rules[0].versionName.pattern",
            )
        for ((body, field) in bodies) {
            val groupText = "{key:1,name:'Group',$body}"
            val appText = "{id:'app.id',groups:[$groupText]}"
            val subscription =
                RawSubscriptionParser.parse("{id:1,name:'Test',version:1,apps:[$appText]}")
            val group = subscription.apps.single().groups.single()
            val failure = assertInvalid(subscription, group, field)
            for (parse in
                listOf<() -> Any>(
                    { parseInput(groupText).parseAppGroup(info.id) },
                    { parseInput(groupText).parseAppGroups(info.id) },
                    { parseInput(appText).parseAppGroups(info.id) },
                    { parseInput(appText).parseApp() },
                )) assertRejected(failure, parse)
        }
    }

    @Test
    fun globalGroupAndRuleAppPatternsBlockExecutionAndGlobalEditor() {
        val bodies =
            listOf(
                "apps:[{id:'app.id',versionName:{pattern:'['}}],rules:[{matches:'*'}]" to
                    "apps[0].versionName.pattern",
                "apps:[{id:'app.id',versionName:{pattern:'['}}],rules:[{matches:'*',apps:[{id:'app.id',versionName:{pattern:'1.*'}}]}]" to
                    "apps[0].versionName.pattern",
                "apps:[{id:'app.id',versionName:{pattern:'1.*'}}],rules:[{matches:'*',apps:[{id:'app.id',versionName:{pattern:'['}}]}]" to
                    "rules[0].apps[0].versionName.pattern",
            )
        for ((body, field) in bodies) {
            val groupText = "{key:1,name:'Group',$body}"
            val subscription =
                RawSubscriptionParser.parse(
                    "{id:1,name:'Test',version:1,globalGroups:[$groupText]}",
                )
            val failure = assertInvalid(subscription, subscription.globalGroups.single(), field)
            assertRejected(failure) { parseInput(groupText).parseGlobalGroup() }
        }
    }

    @Test
    fun validPatternsKeepAppAndGlobalInheritanceAndExplicitOverrides() {
        val appGroup =
            parseInput(
                    """{
            key:1,name:'Group',versionName:{pattern:'1[.].*'},rules:[
                {matches:'*'},
                {matches:'*',versionName:{pattern:'2[.].*'}},
                {matches:'*',versionName:{}}
            ]
        }""",
                )
                .parseAppGroup(info.id)
                .expectValue()
        assertEquals(
            listOf(true, false, true),
            appGroup.rules.map {
                RuleScopePolicy.appVersionMatches(appGroup, it, info)
            },
        )
        val globalGroup =
            parseInput(
                    """{
            key:1,name:'Group',apps:[{id:'app.id',versionName:{pattern:'1[.].*'}}],rules:[
                {matches:'*'},
                {matches:'*',apps:[{id:'app.id',versionName:{pattern:'2[.].*'}}]},
                {matches:'*',apps:[{id:'app.id',versionName:{}}]}
            ]
        }""",
                )
                .parseGlobalGroup()
                .expectValue()
        assertEquals(
            listOf(true, false, true),
            globalGroup.rules.map {
                RuleScopePolicy.versionMatches(
                    RuleScopePolicy.globalApp(globalGroup, it, info.id)!!,
                    info,
                )
            },
        )
    }

    private fun assertInvalid(
        subscription: RawSubscription,
        group: RawSubscription.RawGroupProps,
        field: String,
    ): RuleValidationError.InvalidVersionPattern {
        val failure = group.validationError as RuleValidationError.InvalidVersionPattern
        assertEquals(field, failure.field)
        assertEquals("[", failure.source)
        assertTrue(failure.detail.isNotEmpty())
        assertFalse(group.valid)
        assertFalse(RuleGroupPolicy.getGroupEnabled(group, null))
        val control =
            RuleGroupPolicy.controlState(
                subscription,
                group,
                info.id,
                SubscriptionConfigSnapshot(),
                info,
                "launcher",
                emptySet(),
            )
        assertTrue(control.canEnable)
        assertFalse(control.available)
        assertEquals(
            failure.message,
            control.restrictions.filterIsInstance<RuleRestriction.Invalid>().single().message,
        )
        return failure
    }

    private fun assertRejected(
        failure: RuleValidationError.InvalidVersionPattern,
        parse: () -> Any,
    ) {
        val rejected = assertRejected { parse() }
        assertEquals(SubscriptionFailureReason.RuleInvalidVersionPattern, rejected.reason)
        assertEquals(failure.field, rejected.errorPath)
        assertEquals(failure.message, rejected.errorMessage)
    }
}
