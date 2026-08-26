package li.gkd.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import li.gkd.app.a11y.A11yRuleEngine
import li.gkd.app.appScope
import li.gkd.app.util.LogUtils
import li.gkd.app.snapshot.SnapshotCapture
import li.gkd.app.util.launchTry
import li.gkd.app.util.toast

class SnapshotTileService() : BaseTileService() {
    override val activeFlow = MutableStateFlow(false)

    init {
        onTileClicked { execSnapshot() }
    }
}

private fun execSnapshot() {
    LogUtils.d("SnapshotTileService::onClick")
    val service = A11yRuleEngine.instance
    if (service == null) {
        A11yRuleEngine.performActionBack()
        toast("服务未连接", forced = true)
        return
    }
    appScope.launchTry(Dispatchers.IO) {
        val oldAppId = service.safeActiveWindowAppId

        if (oldAppId == null) {
            A11yRuleEngine.performActionBack()
            toast("获取信息根节点失败", forced = true)
            return@launchTry
        }

        val startTime = System.currentTimeMillis()
        fun timeout(): Boolean {
            return System.currentTimeMillis() - startTime > 3000L
        }

        var ok = false
        while (isActive) {
            val latestAppId = service.safeActiveWindowAppId
            if (latestAppId == null) {
                // https://github.com/gkd-kit/gkd/issues/713
                delay(250)
                if (timeout()) {
                    toast("当前应用没有无障碍信息，捕获失败", forced = true)
                    break
                }
            } else if (latestAppId != oldAppId) {
                ok = true
                LogUtils.d("SnapshotTileService::eventExecutor.execute")
                appScope.launchTry { SnapshotCapture.capture(forcedCropStatusBar = true) }
                break
            } else {
                A11yRuleEngine.performActionBack()
                delay(500)
                if (timeout()) {
                    toast("未检测到界面切换，捕获失败", forced = true)
                    break
                }
            }
        }
        if (!ok) {
            A11yRuleEngine.performActionBack()
        }
    }
}
