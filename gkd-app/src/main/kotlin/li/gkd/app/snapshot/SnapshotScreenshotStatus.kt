package li.gkd.app.snapshot

import li.gkd.app.text.UiStrings

enum class SnapshotScreenshotStatus {
    Captured,
    Unavailable,
    LikelyProtected,
    ;

    fun detailText(): String? = when (this) {
        Captured -> null
        Unavailable -> UiStrings.screenshot_frame_missing
        LikelyProtected -> UiStrings.screenshot_may_be_protected
    }
}
