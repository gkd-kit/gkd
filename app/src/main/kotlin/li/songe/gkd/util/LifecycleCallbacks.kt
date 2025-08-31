package li.songe.gkd.util


import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.isActivityVisible
import java.util.WeakHashMap
import kotlin.reflect.jvm.jvmName

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

    fun useLogLifecycle() {
        onCreated { LogUtils.d("onCreated:" + this::class.jvmName) }
        onDestroyed { LogUtils.d("onDestroyed:" + this::class.jvmName) }
        if (this is OnA11yLife) {
            onA11yConnected { LogUtils.d("onA11yConnected:" + this::class.jvmName) }
        }
        if (this is OnTileLife) {
            onTileClicked { LogUtils.d("onTileClicked:" + this::class.jvmName) }
        }
    }

    fun useScope(): CoroutineScope = MainScope().apply { onDestroyed { cancel() } }

    fun useAliveFlow(stateFlow: MutableStateFlow<Boolean>) {
        onCreated { stateFlow.value = true }
        onDestroyed { stateFlow.value = false }
    }

    fun useAliveToast(name: String, onlyWhenVisible: Boolean = false, delayMillis: Long = 0L) {
        onCreated {
            if (isActivityVisible() || !onlyWhenVisible) {
                toast("${name}已启动", delayMillis)
            }
        }
        onDestroyed {
            if (isActivityVisible() || !onlyWhenVisible) {
                toast("${name}已停止")
            }
        }
    }
}

interface OnA11yLife : OnSimpleLife {
    fun onA11yConnected(f: CbFn) = cbs<CbFn>(3).add(f)
    fun onA11yConnected() = cbs<CbFn>(3).forEach { it() }

    val a11yEventCbs: MutableList<(AccessibilityEvent) -> Unit>
    fun onA11yEvent(f: (AccessibilityEvent) -> Unit) = a11yEventCbs.add(f)
    fun onA11yEvent(event: AccessibilityEvent) = a11yEventCbs.forEach { it(event) }
}

interface OnTileLife : OnSimpleLife {
    fun onStartListened(f: CbFn) = cbs<CbFn>(4).add(f)
    fun onStartListened() = cbs<CbFn>(4).forEach { it() }

    fun onStopListened(f: CbFn) = cbs<CbFn>(5).add(f)
    fun onStopListened() = cbs<CbFn>(5).forEach { it() }

    fun onTileClicked(f: CbFn) = cbs<CbFn>(6).add(f)
    fun onTileClicked() = cbs<CbFn>(6).forEach { it() }
}
