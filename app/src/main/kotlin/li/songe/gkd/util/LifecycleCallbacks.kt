package li.songe.gkd.util


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.loc.Loc
import java.util.WeakHashMap

private val cbMap = WeakHashMap<Any, HashMap<Int, MutableList<Any>>>()

typealias CbFn = () -> Unit

@Suppress("UNCHECKED_CAST")
private fun <T> OnSimpleLife.cbs(method: Int): MutableList<T> = synchronized(cbMap) {
    return cbMap.getOrPut(this) { hashMapOf() }
        .getOrPut(method) { mutableListOf() } as MutableList<T>
}

interface OnSimpleLife {
    fun onCreated(f: CbFn) = cbs<CbFn>(1).add(f)
    fun onCreated() = cbs<CbFn>(1).forEach { it() }

    fun onDestroyed(f: CbFn) = cbs<CbFn>(2).add(f)
    fun onDestroyed() = cbs<CbFn>(2).forEach { it() }

    @Loc
    fun useLogLifecycle(@Loc loc: String = "") {
        onCreated { LogUtils.d("onCreated -> " + this::class.simpleName, loc = loc) }
        onDestroyed { LogUtils.d("onDestroyed -> " + this::class.simpleName, loc = loc) }
        if (this is OnA11yLife) {
            onA11yConnected {
                LogUtils.d(
                    "onA11yConnected -> " + this::class.simpleName,
                    loc = loc,
                )
            }
        }
        if (this is OnTileLife) {
            onTileClicked { LogUtils.d("onTileClicked -> " + this::class.simpleName, loc = loc) }
        }
    }

    val scope: CoroutineScope
    fun useScope(): CoroutineScope = MainScope().apply { onDestroyed { cancel() } }

    fun useAliveFlow(stateFlow: MutableStateFlow<Boolean>) {
        onCreated { stateFlow.value = true }
        onDestroyed { stateFlow.value = false }
    }

    @Loc
    fun useAliveToast(
        name: String,
        delayMillis: Long = 0L,
        @Loc loc: String = "",
    ) {
        onCreated {
            toast("${name}已启动", loc = loc, delayMillis = delayMillis)
        }
        onDestroyed {
            toast("${name}已关闭", loc = loc)
        }
    }
}

interface OnA11yLife : OnSimpleLife {
    fun onA11yConnected(f: CbFn) = cbs<CbFn>(3).add(f)
    fun onA11yConnected() = cbs<CbFn>(3).forEach { it() }
}

interface OnTileLife : OnSimpleLife {
    fun onStartListened(f: CbFn) = cbs<CbFn>(4).add(f)
    fun onStartListened() = cbs<CbFn>(4).forEach { it() }

    fun onStopListened(f: CbFn) = cbs<CbFn>(5).add(f)
    fun onStopListened() = cbs<CbFn>(5).forEach { it() }

    fun onTileClicked(f: CbFn) = cbs<CbFn>(6).add(f)
    fun onTileClicked() = cbs<CbFn>(6).forEach { it() }
}
