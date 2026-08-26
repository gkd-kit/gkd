package li.songe.gkd.snapshot

enum class SnapshotScreenshotStatus {
    Captured,
    Unavailable,
    LikelyProtected,
    ;

    fun detailText(): String? = when (this) {
        Captured -> null
        Unavailable -> "未获取到屏幕画面"
        LikelyProtected -> "当前界面可能受截图保护"
    }
}
