package li.gkd.app.snapshot

/** Explicit values are part of the persisted settings contract. */
enum class SnapshotDisplayMode(val value: Int) {
    ByTime(1), ByApp(2);
}
