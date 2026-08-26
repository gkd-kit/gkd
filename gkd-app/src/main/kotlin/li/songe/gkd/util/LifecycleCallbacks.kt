package li.songe.gkd.util


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.songe.loc.Loc

typealias CbFn = () -> Unit

@Suppress("UNCHECKED_CAST")
private fun <T> OnSimpleLife.cbs(method: Int): MutableList<T> = synchronized(this) {
    return cbMap.getOrPut(method) { mutableListOf() } as MutableList<T>
}

interface OnSimpleLife {
    val cbMap: HashMap<Int, MutableList<Any>>

    fun onCreated(f: CbFn) = cbs<CbFn>(1).add(f)
    fun onCreated() = cbs<CbFn>(1).forEach { it() }

    fun onDestroyed(f: CbFn) = cbs<CbFn>(2).add(f)
    fun onDestroyed() = cbs<CbFn>(2).forEach { it() }

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

    fun useAliveFlow(stateFlow: MutableStateFlow<Boolean>) {
        onCreated { stateFlow.value = true }
        onDestroyed { stateFlow.value = false }
    }

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

    fun runScopePost(delayMillis: Long, r: Runnable) {
        if (delayMillis == 0L && isMainThread) {
            r.run()
            return
        }
        scope.launch(Dispatchers.Main) {
            delay(delayMillis)
            r.run()
        }
    }
}

open class DefaultSimpleLifeImpl : OnSimpleLife {
    override val cbMap: HashMap<Int, MutableList<Any>> = hashMapOf()
    override val scope: CoroutineScope by lazy { MainScope().apply { onDestroyed { cancel() } } }
}

interface OnA11yLife : OnSimpleLife {
    fun onA11yConnected(f: CbFn) = cbs<CbFn>(3).add(f)
    fun onA11yConnected() = cbs<CbFn>(3).forEach { it() }
}

class DefaultTileLifeImpl : DefaultSimpleLifeImpl(), OnTileLife

interface OnTileLife : OnSimpleLife {
    fun onStartListened(f: CbFn) = cbs<CbFn>(4).add(f)
    fun onStartListened() = cbs<CbFn>(4).forEach { it() }

    fun onStopListened(f: CbFn) = cbs<CbFn>(5).add(f)
    fun onStopListened() = cbs<CbFn>(5).forEach { it() }

    fun onTileClicked(f: CbFn) = cbs<CbFn>(6).add(f)
    fun onTileClicked() = cbs<CbFn>(6).forEach { it() }
}

class DefaultA11yLifeImpl : DefaultSimpleLifeImpl(), OnA11yLife
