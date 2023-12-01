package li.songe.gkd.composition

import android.app.Activity
import android.app.Service
import android.content.Context
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

object CompositionExt {
    fun CanOnDestroy.useScope(context: CoroutineContext = Dispatchers.Default): CoroutineScope {
        val scope = CoroutineScope(context)
        onDestroy { scope.cancel() }
        return scope
    }

    fun Context.useLifeCycleLog() {
        val simpleName = this::class.simpleName
        when (this) {
            is Activity, is Service -> {
                LogUtils.d(simpleName, "onCreate")
            }

            else -> {
                LogUtils.w("current context is not the one of Activity, Service", this)
            }
        }

        if (this is CanOnDestroy) {
            onDestroy {
                LogUtils.d(simpleName, "onDestroy")
            }
        }
        if (this is CanOnInterrupt) {
            onInterrupt {
                LogUtils.d(simpleName, "onInterrupt")
            }
        }

        if (this is CanOnServiceConnected) {
            onServiceConnected {
                LogUtils.d(simpleName, "onServiceConnected")
            }
        }

        if (this is CanOnConfigurationChanged) {
            onConfigurationChanged {
                LogUtils.d(simpleName, "onConfigurationChanged", it)
            }
        }

    }
}