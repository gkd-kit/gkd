package li.gkd.app.settings

/** Explicit values are part of the persisted settings contract. */
enum class AutomatorMode(val value: Int) {
    A11y(1), Automation(2);
}
