package li.gkd.app.snapshot

import li.gkd.app.resources.Res
import li.gkd.app.resources.screenshot_frame_missing
import li.gkd.app.resources.screenshot_may_be_protected
import li.gkd.app.ui.text.getSync

fun SnapshotScreenshotStatus.detailText(): String? = when (this) {
    SnapshotScreenshotStatus.Captured -> null
    SnapshotScreenshotStatus.Unavailable -> Res.string.screenshot_frame_missing.getSync()
    SnapshotScreenshotStatus.LikelyProtected -> Res.string.screenshot_may_be_protected.getSync()
}
