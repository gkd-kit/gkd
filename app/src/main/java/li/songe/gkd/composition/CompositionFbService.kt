package li.songe.gkd.composition

import com.torrydo.floatingbubbleview.FloatingBubble
import com.torrydo.floatingbubbleview.FloatingBubbleService

open class CompositionFbService(
    private val block: CompositionFbService.() -> Unit,
) : FloatingBubbleService(), CanOnDestroy, CanSetupBubble {
    override fun onCreate() {
        block()
        super.onCreate()
    }

    private val destroyHooks by lazy { linkedSetOf<() -> Unit>() }
    override fun onDestroy(f: () -> Unit) = destroyHooks.add(f)
    override fun onDestroy() {
        super.onDestroy()
        destroyHooks.forEach { f -> f() }
    }

    override fun setupBubble(action: FloatingBubble.Action): FloatingBubble.Builder {
        var result: FloatingBubble.Builder? = null

        setupBubbleHooks.forEach { f ->
            f(action) {
                result = it
            }
        }
        return result ?: throw error("setupBubble result must be resolved")
    }

    private val setupBubbleHooks by lazy { linkedSetOf<SetupBubbleHook>() }
    override fun setupBubble(f: SetupBubbleHook) {
        setupBubbleHooks.add(f)
    }


}