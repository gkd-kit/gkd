package li.gkd.app.settings

import li.gkd.app.model.Option
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

/** Explicit values are part of the persisted settings contract. */
enum class UpdateChannel(
    override val value: Int,
    override val labelResource: StringResource,
    val url: String,
) : Option<Int> {
    Stable(
        0,
        Res.string.update_channel_stable,
        AppLinks.StableUpdate,
    ),
    Beta(
        1,
        Res.string.update_channel_beta,
        AppLinks.BetaUpdate,
    );

    override val options
        get() = entries
}
