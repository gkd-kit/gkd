package li.gkd.app.settings

/** Explicit values are part of the persisted settings contract. */
enum class UpdateChannel(val value: Int) {
    Stable(0), Beta(1);
}
