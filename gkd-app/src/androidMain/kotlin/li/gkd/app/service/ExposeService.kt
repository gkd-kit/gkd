package li.gkd.app.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import li.gkd.app.appScope
import li.gkd.app.external.ExternalCalls
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotCaptureHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.ThreadUtils
import li.gkd.app.util.ToastUtils

class ExposeService : Service() {
    override fun onBind(intent: Intent?): Binder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        appScope.launchUi {
            try {
                handleIntent(intent)
            } finally {
                ThreadUtils.runMainOrPost(1000) { stopSelf() }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private suspend fun handleIntent(intent: Intent?) {
        if (!ExternalCalls.accepts(intent?.getStringExtra("token"))) {
            ToastUtils.show(Res.string.external_calls_invalid_token.getSync(), forced = true)
            return
        }
        val method = intent?.getStringExtra("method")
        val data = intent?.getStringExtra("data")
        when (method) {
            ACTION_CAPTURE_SNAPSHOT -> SnapshotCaptureHost.capture()
            else -> {
                ToastUtils.show(
                    Res.string.external_call_unknown.getSync(method, data),
                    forced = true
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationCatalog.expose().startForeground()
    }

    companion object {
        private const val ACTION_CAPTURE_SNAPSHOT = "capture-snapshot"
    }
}
