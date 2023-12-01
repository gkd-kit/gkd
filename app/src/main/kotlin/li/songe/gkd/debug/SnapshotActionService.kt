package li.songe.gkd.debug

import android.app.Service
import android.content.Intent
import android.os.Binder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import li.songe.gkd.util.launchTry

/**
 * https://github.com/gkd-kit/gkd/issues/253
 */
class SnapshotActionService : Service() {
    override fun onBind(intent: Intent?): Binder? = null
    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            delay(1000)
            stopSelf()
        }
        appScope.launchTry {
            SnapshotExt.captureSnapshot()
        }
    }
}