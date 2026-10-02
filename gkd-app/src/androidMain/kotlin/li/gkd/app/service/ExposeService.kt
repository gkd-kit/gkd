package li.gkd.app.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.platform.lifecycle.RuntimeStateSynchronizer
import li.gkd.app.resources.Res
import li.gkd.app.resources.execution_success
import li.gkd.app.resources.external_call_unknown
import li.gkd.app.snapshot.SnapshotCaptureHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ThreadUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.componentName

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

    suspend fun handleIntent(intent: Intent?) {
        val expose =
            intent?.getIntExtra("expose", ACTION_CAPTURE_SNAPSHOT) ?: ACTION_CAPTURE_SNAPSHOT
        val data = intent?.getStringExtra("data")
        LogUtils.d("ExposeService::handleIntent", expose, data)
        when (expose) {
            ACTION_START_STATUS -> StatusService.autoStart()
            ACTION_CAPTURE_SNAPSHOT -> SnapshotCaptureHost.capture()
            ACTION_SYNC_RUNTIME -> {
                ToastUtils.show(Res.string.execution_success.getSync(), forced = true)
                RuntimeStateSynchronizer.requestSync()
            }

            else -> {
                ToastUtils.show(
                    Res.string.external_call_unknown.getSync(expose, data),
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
        // Public expose.sh/Intent protocol: preserve these numeric values.
        const val ACTION_START_STATUS = -1
        const val ACTION_CAPTURE_SNAPSHOT = 0
        const val ACTION_SYNC_RUNTIME = 1

        fun initCommandFile() {
            val commandText = template
                .replace("{service}", ExposeService::class.componentName.flattenToShortString())
            AndroidStorage.storage.sh.resolve("expose.sh").writeText(commandText)
        }

        fun exposeIntent(expose: Int, data: String? = null): Intent {
            return Intent(app, ExposeService::class.java).apply {
                putExtra("expose", expose)
                if (data != null) {
                    putExtra("data", data)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

private const val template = $$"""set -euo pipefail
echo '> start expose.sh'
p=''
if [ -n "${1:-}" ]; then
  p+=" --ei expose $1"
fi
if [ -n "${2:-}" ]; then
  p+=" --es data $2"
fi
am start-foreground-service -n {service} $p
echo '> expose.sh end'
"""
