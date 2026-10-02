package li.gkd.app.app

/** Explicit values are part of the persisted settings contract. */
enum class AppSort(val value: Int) {
    ByAppName(0), ByActionTime(2), ByUsedTime(3);
}
