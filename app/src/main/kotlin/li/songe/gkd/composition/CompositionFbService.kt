package li.songe.gkd.composition

import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import com.torrydo.floatingbubbleview.service.expandable.ExpandableBubbleService

open class CompositionFbService(
    private val block: CompositionFbService.() -> Unit,
) : ExpandableBubbleService(), CanOnDestroy, CanConfigBubble {


    override fun configExpandedBubble() = null

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


    private val configBubbleHooks by lazy { linkedSetOf<ConfigBubbleHook>() }
    override fun configBubble(f: ConfigBubbleHook) {
        configBubbleHooks.add(f)
    }

    override fun configBubble(): BubbleBuilder? {
        var result: BubbleBuilder? = null
        configBubbleHooks.forEach { f ->
            f {
                result = it
            }
        }
        return result
    }

}