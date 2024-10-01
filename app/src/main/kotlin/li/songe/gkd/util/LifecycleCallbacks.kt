package li.songe.gkd.util


import android.view.accessibility.AccessibilityEvent
import java.util.WeakHashMap

private val callbacksMap by lazy { WeakHashMap<Any, HashMap<Int, MutableList<Any>>>() }

@Suppress("UNCHECKED_CAST")
private fun <T> Any.getCallbacks(method: Int): MutableList<T> {
    return callbacksMap.getOrPut(this) { hashMapOf() }
        .getOrPut(method) { mutableListOf() } as MutableList<T>
}

interface CanOnCallback

interface OnCreate : CanOnCallback {
    fun onBeforeCreate(f: () -> Unit) {
        getCallbacks<() -> Unit>(1).add(f)
    }

    fun onBeforeCreate() {
        getCallbacks<() -> Unit>(1).forEach { it() }
    }

    fun onCreated(f: () -> Unit) {
        getCallbacks<() -> Unit>(2).add(f)
    }

    fun onCreated() {
        getCallbacks<() -> Unit>(2).forEach { it() }
    }
}

interface OnDestroy : CanOnCallback {
    fun onDestroyed(f: () -> Unit) {
        getCallbacks<() -> Unit>(4).add(f)
    }

    fun onDestroyed() {
        getCallbacks<() -> Unit>(4).forEach { it() }
    }
}

interface OnA11yEvent : CanOnCallback {
    val a11yEventCallbacks: MutableList<(AccessibilityEvent) -> Unit>
        get() = getCallbacks(6)

    fun onA11yEvent(f: (AccessibilityEvent) -> Unit) {
        a11yEventCallbacks.add(f)
    }

    fun onA11yEvent(event: AccessibilityEvent) {
        a11yEventCallbacks.forEach { it(event) }
    }
}

interface OnA11yConnected : CanOnCallback {
    fun onA11yConnected(f: () -> Unit) {
        getCallbacks<() -> Unit>(8).add(f)
    }

    fun onA11yConnected() {
        getCallbacks<() -> Unit>(8).forEach { it() }
    }
}

interface OnChangeListen : CanOnCallback {
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
}

interface OnTileClick {
    fun onTileClicked(f: () -> Unit) {
        getCallbacks<() -> Unit>(14).add(f)
    }

    fun onTileClicked() {
        getCallbacks<() -> Unit>(14).forEach { it() }
    }
}