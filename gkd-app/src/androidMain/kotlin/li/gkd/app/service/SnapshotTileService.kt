package li.gkd.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.appScope
import li.gkd.app.resources.Res
import li.gkd.app.resources.service_not_connected
import li.gkd.app.resources.snapshot_activity_switch_missing
import li.gkd.app.resources.snapshot_app_a11y_missing
import li.gkd.app.resources.snapshot_root_node_failed
import li.gkd.app.snapshot.SnapshotCaptureHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils

class SnapshotTileService : BaseTileService() {
    override val activeFlow = flowOf(false)

    override fun onTileClick() = execSnapshot()
}

private fun execSnapshot() {
    LogUtils.d("SnapshotTileService::onClick")
    val service = A11yRuntime.service
    if (service == null) {
        A11yRuntime.performActionBack()
        ToastUtils.show(Res.string.service_not_connected.getSync(), forced = true)
        return
    }
    appScope.launchUi(Dispatchers.IO) {
        val oldAppId = A11yRuntime.getRoot(service)?.packageName?.toString()

        if (oldAppId == null) {
            A11yRuntime.performActionBack()
            ToastUtils.show(Res.string.snapshot_root_node_failed.getSync(), forced = true)
            return@launchUi
        }

        val startTime = System.currentTimeMillis()
        fun timeout(): Boolean {
            return System.currentTimeMillis() - startTime > 3000L
        }

        var ok = false
        while (isActive) {
            val latestAppId = A11yRuntime.getRoot(service)?.packageName?.toString()
            if (latestAppId == null) {
                // https://github.com/gkd-kit/gkd/issues/713
                delay(250)
                if (timeout()) {
                    ToastUtils.show(Res.string.snapshot_app_a11y_missing.getSync(), forced = true)
                    break
                }
            } else if (latestAppId != oldAppId) {
                ok = true
                LogUtils.d("SnapshotTileService::eventExecutor.execute")
                SnapshotCaptureHost.capture(forcedCropStatusBar = true)
                break
            } else {
                A11yRuntime.performActionBack()
                delay(500)
                if (timeout()) {
                    ToastUtils.show(
                        Res.string.snapshot_activity_switch_missing.getSync(),
                        forced = true
                    )
                    break
                }
            }
        }
        if (!ok) {
            A11yRuntime.performActionBack()
        }
    }
}
