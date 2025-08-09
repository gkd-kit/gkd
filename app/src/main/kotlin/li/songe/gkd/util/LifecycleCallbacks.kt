package li.songe.gkd.util


import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.isActivityVisible
import java.util.WeakHashMap

private val callbacksMap = WeakHashMap<Any, HashMap<Int, MutableList<Any>>>()

@Suppress("UNCHECKED_CAST")
private fun <T> CanOnCallback.getCallbacks(method: Int): MutableList<T> {
    return callbacksMap.getOrPut(this) { hashMapOf() }
        .getOrPut(method) { mutableListOf() } as MutableList<T>
}

interface CanOnCallback {
    fun useLogLifecycle() {
        LogUtils.d("useLogLifecycle", this)
        if (this is OnCreateToDestroy) {
            onCreated { LogUtils.d("onCreated", this) }
            onDestroyed { LogUtils.d("onDestroyed", this) }
        }
        if (this is OnA11yLife) {
            onA11yConnected { LogUtils.d("onA11yConnected", this) }
        }
        if (this is OnTileLife) {
            onStartListened { LogUtils.d("onStartListened", this) }
            onStopListened { LogUtils.d("onStopListened", this) }
            onTileClicked { LogUtils.d("onTileClicked", this) }
        }
    }
}

interface OnCreateToDestroy : CanOnCallback {
    fun onCreated(f: () -> Unit) {
        getCallbacks<() -> Unit>(2).add(f)
    }

    fun onCreated() {
        getCallbacks<() -> Unit>(2).forEach { it() }
    }

    fun onDestroyed(f: () -> Unit) {
        getCallbacks<() -> Unit>(4).add(f)
    }

    fun onDestroyed() {
        getCallbacks<() -> Unit>(4).forEach { it() }
    }

    fun useScope(): CoroutineScope = MainScope().apply { onDestroyed { cancel() } }

    fun useAliveFlow(stateFlow: MutableStateFlow<Boolean>) {
        onCreated { stateFlow.value = true }
        onDestroyed { stateFlow.value = false }
    }

    fun useAliveToast(name: String, onlyWhenVisible: Boolean = false) {
        onCreated {
            if (isActivityVisible() || !onlyWhenVisible) {
                toast("${name}已启动")
            }
        }
        onDestroyed {
            if (isActivityVisible() || !onlyWhenVisible) {
                toast("${name}已停止")
            }
        }
    }
}

interface OnA11yLife : CanOnCallback {
    fun onA11yConnected(f: () -> Unit) {
        getCallbacks<() -> Unit>(8).add(f)
    }

    fun onA11yConnected() {
        getCallbacks<() -> Unit>(8).forEach { it() }
    }

    val a11yEventCallbacks: MutableList<(AccessibilityEvent) -> Unit>

    fun onA11yEvent(f: (AccessibilityEvent) -> Unit) {
        a11yEventCallbacks.add(f)
    }

    fun onA11yEvent(event: AccessibilityEvent) {
        a11yEventCallbacks.forEach { it(event) }
    }
}

interface OnTileLife : CanOnCallback {
    fun onStartListened(f: () -> Unit) {
        getCallbacks<() -> Unit>(10).add(f)
    }

    fun onStartListened() {
        getCallbacks<() -> Unit>(10).forEach { it() }
    }

    fun onStopListened(f: () -> Unit) {
        getCallbacks<() -> Unit>(12).add(f)
    }

    fun onStopListened() {
        getCallbacks<() -> Unit>(12).forEach { it() }
    }

    fun onTileClicked(f: () -> Unit) {
        getCallbacks<() -> Unit>(14).add(f)
    }

    fun onTileClicked() {
        getCallbacks<() -> Unit>(14).forEach { it() }
    }
}