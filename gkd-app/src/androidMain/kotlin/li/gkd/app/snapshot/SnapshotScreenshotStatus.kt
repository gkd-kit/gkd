package li.gkd.app.snapshot

import li.gkd.app.resources.*
import li.gkd.app.ui.text.getSync

fun SnapshotScreenshotStatus.detailText(): String? = when (this) {
    SnapshotScreenshotStatus.Captured -> null
    SnapshotScreenshotStatus.Unavailable -> Res.string.screenshot_frame_missing.getSync()
    SnapshotScreenshotStatus.LikelyProtected -> Res.string.screenshot_may_be_protected.getSync()
}
