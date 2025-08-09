package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import li.songe.gkd.appScope
import li.songe.gkd.notif.snapshotActionNotif
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.launchTry

/**
 * https://github.com/gkd-kit/gkd/issues/253
 */
class SnapshotActionService : Service() {
    override fun onBind(intent: Intent?): Binder? = null
    override fun onCreate() {
        super.onCreate()
        snapshotActionNotif.notifyService()
        appScope.launchTry {
            try {
                SnapshotExt.captureSnapshot()
            } finally {
                stopSelf()
            }
        }
    }
}