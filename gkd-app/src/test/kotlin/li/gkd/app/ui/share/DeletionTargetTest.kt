package li.gkd.app.ui.share

import li.gkd.app.feature.subscription.CategoryEditorRoute
import li.gkd.app.feature.subscription.RuleExcludeEditorRoute
import li.gkd.app.feature.subscription.SubsAppGroupListRoute
import li.gkd.app.feature.subscription.SubsAppListRoute
import li.gkd.app.feature.subscription.SubsCategoryGroupRoute
import li.gkd.app.feature.subscription.SubsCategoryRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupExcludeRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupListRoute
import li.gkd.app.feature.subscription.UpsertRuleGroupRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionTargetTest {
    @Test
    fun categoryTargetOwnsItsDetailRoutesOnly() {
        val target = DeletionTarget.Category(1, 2)
        assertTrue(target.owns(SubsCategoryGroupRoute(1, 2)))
        assertTrue(target.owns(CategoryEditorRoute(1, 2)))
        assertFalse(target.owns(SubsCategoryRoute(1)))
        assertFalse(target.owns(SubsCategoryGroupRoute(1, 3)))
        assertFalse(target.owns(SubsCategoryGroupRoute(4, 2)))
        assertFalse(target.owns(CategoryEditorRoute(1)))
    }

    @Test
    fun groupTargetOwnsMatchingEditorsOnly() {
        val target = DeletionTarget.Group(1, "app", 2)
        assertTrue(target.owns(RuleExcludeEditorRoute(1, 2, "app")))
        assertTrue(target.owns(UpsertRuleGroupRoute(1, 2, "app")))
        assertFalse(target.owns(SubsAppGroupListRoute(1, "app", focusGroupKey = 2)))
        assertFalse(target.owns(SubsGlobalGroupExcludeRoute(1, 2)))
        assertFalse(target.owns(RuleExcludeEditorRoute(1, 2, "other")))
        assertFalse(target.owns(RuleExcludeEditorRoute(3, 2, "app")))
        val global = DeletionTarget.Group(1, null, 2)
        assertTrue(global.owns(SubsGlobalGroupExcludeRoute(1, 2)))
        assertFalse(global.owns(SubsGlobalGroupListRoute(1, focusGroupKey = 2)))
    }

    @Test
    fun appTargetOwnsMatchingAppRoutesOnly() {
        val target = DeletionTarget.App(1, "app")
        assertTrue(target.owns(SubsAppGroupListRoute(1, "app")))
        assertTrue(target.owns(UpsertRuleGroupRoute(1, appId = "app")))
        assertFalse(target.owns(SubsAppListRoute(1)))
        assertFalse(target.owns(SubsAppGroupListRoute(1, "other")))
    }

    @Test
    fun subscriptionTargetOwnsItsDescendantRoutesOnly() {
        val target = DeletionTarget.Subscription(1)
        assertTrue(target.owns(SubsAppListRoute(1)))
        assertTrue(target.owns(SubsCategoryGroupRoute(1, 2)))
        assertTrue(target.owns(SubsGlobalGroupExcludeRoute(1, 3)))
        assertFalse(target.owns(SubsAppListRoute(2)))
        assertFalse(target.owns(SubsGlobalGroupExcludeRoute(2, 3)))
    }
}
