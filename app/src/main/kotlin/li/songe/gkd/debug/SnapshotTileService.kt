package li.songe.gkd.debug

import android.accessibilityservice.AccessibilityService
import android.service.quicksettings.TileService
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import li.songe.gkd.debug.SnapshotExt.captureSnapshot
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.TopActivity
import li.songe.gkd.service.getAndUpdateCurrentRules
import li.songe.gkd.service.safeActiveWindow
import li.songe.gkd.service.updateTopActivity
import li.songe.gkd.shizuku.safeGetTopActivity
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
            val oldAppId = service.safeActiveWindow?.packageName?.toString()
                ?: return@launchTry toast("获取界面信息根节点失败")

            val startTime = System.currentTimeMillis()
            fun timeout(): Boolean {
                return System.currentTimeMillis() - startTime > 3000L
            }

            val timeoutText = "没有检测到界面切换,捕获失败"
            while (true) {
                val latestAppId = service.safeActiveWindow?.packageName?.toString()
                if (latestAppId == null) {
                    // https://github.com/gkd-kit/gkd/issues/713
                    delay(250)
                    if (timeout()) {
                        toast(timeoutText)
                        break
                    }
                } else if (latestAppId != oldAppId) {
                    LogUtils.d("SnapshotTileService::eventExecutor.execute")
                    appScope.launch(A11yService.eventThread) {
                        val topActivity = safeGetTopActivity() ?: TopActivity(appId = latestAppId)
                        updateTopActivity(topActivity)
                        getAndUpdateCurrentRules()
                        appScope.launchTry(Dispatchers.IO) {
                            captureSnapshot()
                        }
                    }
                    break
                } else {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(500)
                    if (timeout()) {
                        toast(timeoutText)
                        break
                    }
                }
            }
        }
    }

}