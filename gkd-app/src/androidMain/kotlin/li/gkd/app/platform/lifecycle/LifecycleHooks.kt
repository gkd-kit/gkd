package li.gkd.app.platform.lifecycle

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import li.gkd.app.util.LogUtils
import li.songe.codeorigin.CallSite

class LifecycleHooks(
    private val onLifecycleEvent: (String, String) -> Unit = { event, loc ->
        LogUtils.d(event, loc = loc)
    },
    private val onCleanupError: (Throwable, String) -> Unit = { error, loc ->
        LogUtils.d(error, loc = loc)
    },
) {
    private data class DestroyedCallback(
        val callback: () -> Unit,
        val loc: String,
    )

    private val createdCallbacks = mutableListOf<() -> Unit>()
    private val destroyedCallbacks = ArrayDeque<DestroyedCallback>()
    private var created = false
    private var destroyed = false

    @MainThread
    fun useLogLifecycle(
        owner: Any,
        @CallSite loc: String = "",
    ) {
        val ownerName = owner::class.simpleName
        onCreated { onLifecycleEvent("onCreated -> $ownerName", loc) }
        onDestroyed(loc = loc) { onLifecycleEvent("onDestroyed -> $ownerName", loc) }
    }

    @MainThread
    fun onCreated(callback: () -> Unit) {
        check(!created) { "onCreated must be registered before creation" }
        createdCallbacks += callback
    }

    @MainThread
    fun onDestroyed(
        @CallSite loc: String = "",
        callback: () -> Unit,
    ) {
        check(!destroyed) { "Lifecycle is already destroyed" }
        destroyedCallbacks.addFirst(DestroyedCallback(callback, loc))
    }

    @MainThread
    fun dispatchCreated() {
        if (created) return
        check(!destroyed) { "Lifecycle is already destroyed" }
        created = true
        try {
            createdCallbacks.forEach { it() }
        } finally {
            createdCallbacks.clear()
        }
    }

    @MainThread
    fun dispatchDestroyed() {
        if (destroyed) return
        destroyed = true
        createdCallbacks.clear()
        while (destroyedCallbacks.isNotEmpty()) {
            val destroyedCallback = destroyedCallbacks.removeFirst()
            runCatching { destroyedCallback.callback() }
                .onFailure { onCleanupError(it, destroyedCallback.loc) }
        }
    }
}

@MainThread
fun LifecycleOwner.useLogLifecycle(@CallSite loc: String = "") {
    val ownerName = this::class.simpleName
    onCreated { LogUtils.d("onCreated -> $ownerName", loc = loc) }
    onDestroyed { LogUtils.d("onDestroyed -> $ownerName", loc = loc) }
}

@MainThread
fun LifecycleOwner.onCreated(callback: () -> Unit) {
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            owner.lifecycle.removeObserver(this)
            callback()
        }
    })
}

@MainThread
fun LifecycleOwner.onDestroyed(callback: () -> Unit) {
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            callback()
        }
    })
}
