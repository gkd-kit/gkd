package li.songe.gkd.composition

import android.app.Service
import android.content.Intent
import android.os.IBinder

open class CompositionService(
    private val block: CompositionService.() -> Unit,
) : Service(), CanOnDestroy, CanOnStartCommand {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        block()
    }

    private val onStartCommandHooks by lazy { linkedSetOf<StartCommandHook>() }
    override fun onStartCommand(f: StartCommandHook) = onStartCommandHooks.add(f)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onStartCommandHooks.forEach { f -> f(intent, flags, startId) }
        return super.onStartCommand(intent, flags, startId)
    }

    private val destroyHooks by lazy { linkedSetOf<() -> Unit>() }
    override fun onDestroy(f: () -> Unit) = destroyHooks.add(f)
    override fun onDestroy() {
        super.onDestroy()
        destroyHooks.forEach { f -> f() }
    }

}