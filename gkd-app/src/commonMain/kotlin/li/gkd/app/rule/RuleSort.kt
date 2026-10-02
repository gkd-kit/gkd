package li.gkd.app.rule

/** Explicit values are part of the persisted settings contract. */
enum class RuleSort(val value: Int) {
    ByDefault(0), ByActionTime(1), ByRuleName(2);
}
