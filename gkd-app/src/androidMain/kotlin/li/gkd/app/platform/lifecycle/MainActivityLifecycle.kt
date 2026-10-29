package li.gkd.app.platform.lifecycle

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.atomicfu.atomic
import li.gkd.app.META
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.input.currentTopActivity
import li.gkd.app.matching.input.updateTopActivity
import li.songe.codeorigin.CallSite

private val visibleActivityCount = atomic(0)

object MainActivityVisibility {
    val isVisible: Boolean
        get() = visibleActivityCount.value > 0
}

@MainThread
fun ComponentActivity.useMainActivityLifecycle(@CallSite loc: String = "") {
    lifecycle.addObserver(MainActivityLifecycleObserver(this, loc))
}

private class MainActivityLifecycleObserver(
    private val activity: ComponentActivity,
    private val logLoc: String,
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        LogUtils.i("MainActivity::onStart", loc = logLoc)
        visibleActivityCount.incrementAndGet()
        if (currentTopActivity.appId != META.appId) {
            updateTopActivity(
                META.appId,
                activity.javaClass.name,
            )
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        LogUtils.i("MainActivity::onResume", loc = logLoc)
        RuntimeStateSynchronizer.requestSync(loc = logLoc)
    }

    override fun onStop(owner: LifecycleOwner) {
        LogUtils.i("MainActivity::onStop", loc = logLoc)
        visibleActivityCount.decrementAndGet()
    }
}
