package li.gkd.app.settings

import li.gkd.app.model.Option
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

/** Explicit values are part of the persisted settings contract. */
enum class AutomatorMode(override val value: Int, override val labelResource: StringResource) : Option<Int> {
    A11y(1, Res.string.a11y_label), Automation(2, Res.string.automation_label);

    override val options get() = entries
}
