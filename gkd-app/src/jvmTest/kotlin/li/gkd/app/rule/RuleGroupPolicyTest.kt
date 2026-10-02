package li.gkd.app.rule

import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RuleGroupPolicyTest {
    private val policy = RuleGroupPolicy()

    @Test
    fun explanationDistinguishesSubscriptionInheritanceFromExplicitGroupDefaultsAndManualOverrides() {
        val group = subscription.apps.single().groups.single()
        val category = subscription.categories.single()
        val explicitDefault = SubsCategoryConfig(null, subscription.id, category.key)
        assertEquals(
            RuleEnableDecision(false, RuleEnableSource.SubscriptionCategory),
            policy.explainGroupEnabled(group, null, category, null)
        )
        assertEquals(
            RuleEnableDecision(true, RuleEnableSource.GroupDefault),
            policy.explainGroupEnabled(group, null, category, explicitDefault)
        )
        assertEquals(
            RuleEnableDecision(false, RuleEnableSource.Category),
            policy.explainGroupEnabled(group, null, category, explicitDefault.copy(enable = false))
        )
        assertEquals(
            RuleEnableDecision(true, RuleEnableSource.Manual),
            policy.explainGroupEnabled(
                group, SubsAppGroupConfig(subscription.id, "app.id", group.key, true),
                category, explicitDefault.copy(enable = false)
            )
        )
    }

    private val subscription = RawSubscription.parse(
        """
        {
          id: -2,
          name: 'Local',
          version: 0,
          categories: [{ key: 0, name: 'Batch', enable: false }],
          apps: [{
            id: 'app.id',
            groups: [{ key: 1, name: 'Batch', enable: true, rules: [] }],
          }],
          globalGroups: [{
            key: 2,
            name: 'Global',
            matchLauncher: true,
            matchSystemApp: false,
            matchAnyApp: false,
            apps: [
              { id: 'blocked.app', enable: false },
              { id: 'forced.app', enable: true },
            ],
            rules: [],
          }],
        }
        """.trimIndent(),
    )

    @Test
    fun appGroupConfigurationOverridesCategoryAndGroupDefaults() {
        val group = subscription.apps.single().groups.single()
        val category = subscription.categories.single()

        assertFalse(policy.getGroupEnabled(group, null, category, null))
        assertEquals(
            true,
            policy.getGroupEnabled(
                group,
                null,
                category,
                SubsCategoryConfig(
                    enable = true,
                    subsId = subscription.id,
                    categoryKey = category.key
                ),
            ),
        )
        assertEquals(
            false,
            policy.getGroupEnabled(
                group,
                SubsAppGroupConfig(
                    enable = false,
                    subsId = subscription.id,
                    appId = subscription.apps.single().id,
                    groupKey = group.key,
                ),
                category,
                SubsCategoryConfig(
                    enable = true,
                    subsId = subscription.id,
                    categoryKey = category.key
                ),
            ),
        )
    }

    @Test
    fun explicitFollowStateUsesGroupDefaultWhenCategoryDefaultsToDisabled() {
        val group = subscription.apps.single().groups.single()
        val category = subscription.categories.single()
        val config = SubsCategoryConfig(
            enable = null,
            subsId = subscription.id,
            categoryKey = category.key,
        )

        assertEquals(false, policy.getCategoryEnabled(category, null))
        assertNull(policy.getCategoryEnabled(category, config))
        assertEquals(true, policy.getGroupEnabled(group, null, category, config))
    }

    @Test
    fun explicitFollowStateUsesGroupDefaultWhenCategoryDefaultsToEnabled() {
        val group = subscription.apps.single().groups.single().copy(enable = false)
        val category = subscription.categories.single().copy(enable = true)
        val config = SubsCategoryConfig(
            enable = null,
            subsId = subscription.id,
            categoryKey = category.key,
        )

        assertEquals(true, policy.getCategoryEnabled(category, null))
        assertNull(policy.getCategoryEnabled(category, config))
        assertFalse(policy.getGroupEnabled(group, null, category, config))
    }

    @Test
    fun globalGroupScopeHonorsInnerDisableThenExplicitAndDefaultScopes() {
        val group = subscription.globalGroups.single()
        val emptyExclude = ExcludeData(emptyMap(), emptySet())

        assertNull(
            policy.getGlobalGroupChecked(
                subscription,
                ExcludeData(mapOf("blocked.app" to false), emptySet()),
                group,
                "blocked.app",
                "launcher.app",
                setOf("system.app"),
            ),
        )
        assertEquals(
            true,
            policy.getGlobalGroupChecked(
                subscription,
                ExcludeData(mapOf("ordinary.app" to false), emptySet()),
                group,
                "ordinary.app",
                "launcher.app",
                setOf("system.app"),
            ),
        )
        assertEquals(
            false,
            policy.getGlobalGroupChecked(
                subscription,
                ExcludeData(mapOf("ordinary.app" to true), emptySet()),
                group,
                "ordinary.app",
                "launcher.app",
                setOf("system.app"),
            ),
        )
        assertEquals(
            true,
            policy.getGlobalGroupChecked(
                subscription,
                emptyExclude,
                group,
                "forced.app",
                "launcher.app",
                setOf("system.app"),
            ),
        )
        // Launcher permission does not bypass matchAnyApp for unspecified apps.
        assertEquals(
            false,
            policy.getGlobalGroupChecked(
                subscription,
                emptyExclude,
                group,
                "launcher.app",
                "launcher.app",
                setOf("system.app"),
            ),
        )
        assertEquals(
            false,
            policy.getGlobalGroupChecked(
                subscription,
                emptyExclude,
                group,
                "system.app",
                "launcher.app",
                setOf("system.app"),
            ),
        )
        assertEquals(
            false,
            policy.getGlobalGroupChecked(
                subscription,
                emptyExclude,
                group,
                "ordinary.app",
                "launcher.app",
                setOf("system.app"),
            ),
        )
    }
}
