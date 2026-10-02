package li.gkd.app.ui.text

import li.gkd.app.resources.Res
import li.gkd.app.resources.subscription_local
import li.gkd.app.resources.subscription_memory
import li.gkd.app.subscription.SubscriptionDefaults
import org.jetbrains.compose.resources.getString

suspend fun subscriptionDefaults() = SubscriptionDefaults(
    localName = getString(Res.string.subscription_local),
    memoryName = getString(Res.string.subscription_memory),
)
