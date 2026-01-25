package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.notif.exposeNotif
import li.songe.gkd.syncFixState
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.componentName
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.runMainPost
import li.songe.gkd.util.shFolder
import li.songe.gkd.util.toast

class ExposeService : Service() {
    override fun onBind(intent: Intent?): Binder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        appScope.launchTry {
            try {
                handleIntent(intent)
            } finally {
                runMainPost(1000) { stopSelf() }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    suspend fun handleIntent(intent: Intent?) {
        val expose = intent?.getIntExtra("expose", 0) ?: 0
        val data = intent?.getStringExtra("data")
        LogUtils.d("ExposeService::handleIntent", expose, data)
        when (expose) {
            -1 -> StatusService.autoStart()
            0 -> SnapshotExt.captureSnapshot()
            1 -> {
                toast("执行成功", forced = true)
                syncFixState()
            }

            else -> {
                toast("未知调用: expose=$expose data=$data", forced = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        exposeNotif.notifyService()
    }

    companion object {
        fun initCommandFile() {
            val commandText = template
                .replace("{service}", ExposeService::class.componentName.flattenToShortString())
            shFolder.resolve("expose.sh").writeText(commandText)
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