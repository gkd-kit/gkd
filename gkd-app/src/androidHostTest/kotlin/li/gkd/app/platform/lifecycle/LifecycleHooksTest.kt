package li.gkd.app.platform.lifecycle

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    sdk = [28],
)
class LifecycleHooksTest {
    @Test
    fun lifecycleLoggingUsesOwnerAndRegistrationLocation() {
        val events = mutableListOf<String>()
        val hooks =
            LifecycleHooks(
                onLifecycleEvent = { event, loc -> events += "$event@$loc" },
                onCleanupError = { error, _ -> throw error },
            )
        hooks.useLogLifecycle(this, loc = "lifecycle-registration-loc")
        hooks.onCreated { events += "created-callback" }
        hooks.onDestroyed { events += "destroyed-callback" }

        hooks.dispatchCreated()
        hooks.dispatchDestroyed()

        assertEquals(
            listOf(
                "onCreated -> LifecycleHooksTest@lifecycle-registration-loc",
                "created-callback",
                "destroyed-callback",
                "onDestroyed -> LifecycleHooksTest@lifecycle-registration-loc",
            ),
            events,
        )
    }

    @Test
    fun callbacksRunOnceInLifecycleOrder() {
        val events = mutableListOf<String>()
        val hooks = LifecycleHooks { error, _ -> throw error }
        hooks.onCreated { events += "create-first" }
        hooks.onCreated { events += "create-second" }
        hooks.onDestroyed { events += "destroy-first" }
        hooks.onDestroyed { events += "destroy-second" }

        hooks.dispatchCreated()
        hooks.dispatchCreated()
        hooks.dispatchDestroyed()
        hooks.dispatchDestroyed()

        assertEquals(
            listOf("create-first", "create-second", "destroy-second", "destroy-first"),
            events,
        )
    }

    @Test
    fun cleanupFailureDoesNotSkipRemainingCallbacks() {
        val events = mutableListOf<String>()
        val errors = mutableListOf<Pair<Throwable, String>>()
        val hooks = LifecycleHooks { error, loc -> errors += error to loc }
        hooks.onDestroyed { events += "first" }
        hooks.onDestroyed(loc = "failed-cleanup-loc") { error("failed cleanup") }
        hooks.onDestroyed { events += "last" }

        hooks.dispatchDestroyed()

        assertEquals(listOf("last", "first"), events)
        assertEquals(listOf("failed cleanup"), errors.map { it.first.message })
        assertEquals(listOf("failed-cleanup-loc"), errors.map { it.second })
    }
}
