package li.songe.gkd.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.appScope
import li.songe.gkd.notif.exposeNotif
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.componentName
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.shFolder
import li.songe.gkd.util.toast

class ExposeService : Service() {
    override fun onBind(intent: Intent?): Binder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        appScope.launchTry {
            try {
                handleIntent(intent)
            } finally {
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    suspend fun handleIntent(intent: Intent?) {
        val expose = intent?.getIntExtra("expose", 0) ?: 0
        val data = intent?.getStringExtra("data")
        LogUtils.d("ExposeService::handleIntent", expose, data)
        when (expose) {
            0 -> SnapshotExt.captureSnapshot()
            else -> {
                toast("未知调用: expose=$expose data=$data")
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
                .replace("__N__", ExposeService::class.componentName.flattenToShortString())
            shFolder.resolve("expose.sh").writeText(commandText)
        }
    }
}

private const val template = """
p=''
if [ -n "$1" ]; then
  p+=" --ei expose $1"
fi
if [ -n "$2" ]; then
  p+=" --es data $2"
fi
am start-foreground-service -n __N__ ${'$'}p
echo 'Execution Successful'
"""