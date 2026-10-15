package li.gkd.app.rule

import li.gkd.app.model.Option
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

/** Explicit values are part of the persisted settings contract. */
enum class RuleSort(override val value: Int, override val labelResource: StringResource) : Option<Int> {
    ByDefault(0, Res.string.rule_sort_default), ByActionTime(1, Res.string.app_sort_recent_trigger), ByRuleName(2, Res.string.rule_sort_name), ByTriggerCount(3, Res.string.trigger_count_sort);

    override val options get() = entries
}
