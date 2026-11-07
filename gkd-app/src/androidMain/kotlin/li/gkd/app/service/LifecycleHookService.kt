package li.gkd.app.service

import androidx.lifecycle.LifecycleService
import li.gkd.app.notif.ForegroundNotification
import li.gkd.app.notif.NotificationDispatcher
import li.gkd.app.platform.lifecycle.LifecycleHooks
import li.songe.codeorigin.CallSite

abstract class LifecycleHookService : LifecycleService() {
    private val lifecycleHooks = LifecycleHooks()
    private var foregroundNotificationId: Int? = null

    @Synchronized
    fun showForegroundNotification(notification: ForegroundNotification): Boolean {
        if (!NotificationDispatcher.startForeground(this, notification)) return false
        foregroundNotificationId = notification.id
        return true
    }

    fun onCreated(callback: () -> Unit) = lifecycleHooks.onCreated(callback)

    fun onDestroyed(
        @CallSite loc: String = "",
        callback: () -> Unit,
    ) = lifecycleHooks.onDestroyed(loc = loc, callback = callback)

    fun useLogLifecycle(@CallSite loc: String = "") {
        lifecycleHooks.useLogLifecycle(owner = this, loc = loc)
    }

    final override fun onCreate() {
        super.onCreate()
        lifecycleHooks.dispatchCreated()
    }

    final override fun onDestroy() {
        synchronized(this) {
            foregroundNotificationId?.let(NotificationDispatcher::forgetForeground)
            foregroundNotificationId = null
        }
        lifecycleHooks.dispatchDestroyed()
        super.onDestroy()
    }
}
