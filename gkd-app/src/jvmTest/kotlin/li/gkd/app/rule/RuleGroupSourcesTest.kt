package li.gkd.app.rule

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionSnapshot
import li.gkd.db.SubscriptionConfigSnapshot

class RuleGroupSourcesTest {
    @Test
    fun waitsForAllInputsAndDistinguishesEmptySuccessAndFailure() = runBlocking {
        val subscriptions = MutableStateFlow<Loadable<SubscriptionSnapshot>>(Loadable.Loading)
        val configs = MutableSharedFlow<SubscriptionConfigSnapshot>(replay = 1)
        val environment = MutableStateFlow(RuleAppEnvironment(emptyMap(), "launcher"))
        val events = CopyOnWriteArrayList<Loadable<RuleGroupSnapshot>>()
        val job = launch {
            RuleGroupSources.observe(
                subscriptions,
                environment,
                configs,
                RuleGroupPolicy()
            ).collect { events.add(it) }
        }
        try {
            subscriptions.value = Loadable.Ready(SubscriptionSnapshot())
            yield()
            assertTrue(events.isEmpty(), "No complete database snapshot has arrived")
            configs.emit(SubscriptionConfigSnapshot())
            withTimeout(5000) { while (events.none { it is Loadable.Ready }) delay(5) }
            assertTrue(assertIs<Loadable.Ready<RuleGroupSnapshot>>(events.last()).value.groups.globalGroups.isEmpty())
            val error = IllegalStateException("cannot read subscriptions")
            subscriptions.value = Loadable.Failure(error)
            withTimeout(5000) { while (events.last() !is Loadable.Failure) delay(5) }
            assertSame(error, assertIs<Loadable.Failure>(events.last()).cause)
            subscriptions.value = Loadable.Loading
            withTimeout(5000) { while (events.last() != Loadable.Loading) delay(5) }
            subscriptions.value = Loadable.Ready(SubscriptionSnapshot())
            withTimeout(5000) { while (events.last() !is Loadable.Ready) delay(5) }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun ruleOverridesReferencedCooldownAndGroupFallbackRemainDistinct() {
        val sub = RawSubscription.parse(
            """{"id":1,"name":"test","version":1,"apps":[{"id":"test.app","groups":[{"key":1,"name":"group","actionCd":900,"matchDelay":20,"rules":[{"key":1,"matches":"*","actionCd":300},{"key":2,"matches":"*","actionCdKey":1},{"key":3,"matches":"*","actionCdKey":99,"matchDelay":0}]}]}]}""",
            json5 = false
        )
        val group = sub.apps.single().groups.single()
        val linked = RuleExecutionParameters(group.rules[1], group)
        assertEquals(300L, linked.actionCd)
        assertEquals(20L, linked.matchDelay)
        val missing = RuleExecutionParameters(group.rules[2], group)
        assertEquals(900L, missing.actionCd)
        assertEquals(0L, missing.matchDelay)
    }
}
