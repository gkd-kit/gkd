package li.songe.gkd.debug

import android.accessibilityservice.AccessibilityService
import android.service.quicksettings.TileService
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.delay
import li.songe.gkd.appScope
import li.songe.gkd.debug.SnapshotExt.captureSnapshot
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.service.topActivityFlow
import li.songe.gkd.util.launchTry

class SnapshotTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (!GkdAbService.isRunning()) {
            ToastUtils.showShort("无障碍没有开启")
            return
        }
        val service = GkdAbService.service ?: return
        val oldAppId = topActivityFlow.value?.appId
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        appScope.launchTry {
            var i = 0
            while (topActivityFlow.value?.appId == oldAppId) {
                delay(100)
                i++
                if (i * 100 > 3000) {
                    ToastUtils.showShort("没有检测到界面切换,捕获失败")
                    return@launchTry
                }
            }
            captureSnapshot()
        }
    }

}