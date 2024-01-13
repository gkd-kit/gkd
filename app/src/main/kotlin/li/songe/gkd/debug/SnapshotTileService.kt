package li.songe.gkd.debug

import android.accessibilityservice.AccessibilityService
import android.service.quicksettings.TileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import li.songe.gkd.appScope
import li.songe.gkd.debug.SnapshotExt.captureSnapshot
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.service.safeActiveWindow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast

class SnapshotTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val service = GkdAbService.service
        if (service == null) {
            toast("无障碍没有开启")
            return
        }
        appScope.launchTry(Dispatchers.IO) {
            val oldAppId = service.safeActiveWindow?.packageName
                ?: return@launchTry toast("获取界面信息根节点失败")
            val interval = 500L
            val waitTime = 3000L
            var i = 0
            while (oldAppId.contentEquals(service.safeActiveWindow?.packageName)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                delay(interval)
                i++
                if (i * interval > waitTime) {
                    toast("没有检测到界面切换,捕获失败")
                    return@launchTry
                }
            }
            captureSnapshot()
        }
    }

}