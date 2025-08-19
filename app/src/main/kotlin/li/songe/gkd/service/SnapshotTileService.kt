package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.service.quicksettings.TileService
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import li.songe.gkd.appScope
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast

class SnapshotTileService : TileService() {
    override fun onClick() {
        super.onClick()
        LogUtils.d("SnapshotTileService::onClick")
        val service = A11yService.instance
        if (service == null) {
            toast("无障碍没有开启")
            return
        }
        appScope.launchTry(Dispatchers.IO) {
            val oldAppId = service.safeActiveWindowAppId
                ?: return@launchTry toast("获取界面信息根节点失败")

            val startTime = System.currentTimeMillis()
            fun timeout(): Boolean {
                return System.currentTimeMillis() - startTime > 3000L
            }

            while (isActive) {
                val latestAppId = service.safeActiveWindowAppId
                if (latestAppId == null) {
                    // https://github.com/gkd-kit/gkd/issues/713
                    delay(250)
                    if (timeout()) {
                        toast("当前应用没有无障碍信息，捕获失败")
                        break
                    }
                } else if (latestAppId != oldAppId) {
                    LogUtils.d("SnapshotTileService::eventExecutor.execute")
                    appScope.launchTry { SnapshotExt.captureSnapshot() }
                    break
                } else {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(500)
                    if (timeout()) {
                        toast("未检测到界面切换，捕获失败")
                        break
                    }
                }
            }
        }
    }
}