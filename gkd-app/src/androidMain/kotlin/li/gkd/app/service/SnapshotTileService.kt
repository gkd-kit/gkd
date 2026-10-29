package li.gkd.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import li.gkd.app.appScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.AndroidAutomationSessions
import li.gkd.app.matching.action.performActionBack
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotCaptureHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.ToastUtils

class SnapshotTileService : BaseTileService() {
    override val activeFlow = flowOf(false)

    override fun onTileClick() = execSnapshot()
}

private fun execSnapshot() {
    LogUtils.i("SnapshotTileService::onClick")
    val service = AndroidAutomationSessions.service
    if (service == null) {
        appScope.launchUi(Dispatchers.IO) {
            performActionBack()
            ToastUtils.show(Res.string.service_not_connected.getSync(), forced = true)
        }
        return
    }
    appScope.launchUi(Dispatchers.IO) {
        val oldAppId = AndroidAutomationSessions.getRoot(service)?.packageName?.toString()

        if (oldAppId == null) {
            performActionBack()
            ToastUtils.show(Res.string.snapshot_root_node_failed.getSync(), forced = true)
            return@launchUi
        }

        val startTime = System.currentTimeMillis()
        fun timeout(): Boolean {
            return System.currentTimeMillis() - startTime > 3000L
        }

        var ok = false
        while (isActive) {
            val latestAppId = AndroidAutomationSessions.getRoot(service)?.packageName?.toString()
            if (latestAppId == null) {
                // https://github.com/gkd-kit/gkd/issues/713
                delay(250)
                if (timeout()) {
                    ToastUtils.show(Res.string.snapshot_app_a11y_missing.getSync(), forced = true)
                    break
                }
            } else if (latestAppId != oldAppId) {
                ok = true
                LogUtils.i("SnapshotTileService::eventExecutor.execute")
                SnapshotCaptureHost.capture(forcedCropStatusBar = true)
                break
            } else {
                performActionBack()
                delay(500)
                if (timeout()) {
                    ToastUtils.show(
                        Res.string.snapshot_activity_switch_missing.getSync(),
                        forced = true,
                    )
                    break
                }
            }
        }
        if (!ok) {
            performActionBack()
        }
    }
}
