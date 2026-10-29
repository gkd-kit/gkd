package li.gkd.app.subscription

import li.gkd.app.appScope
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.option.UpdateTimeOption
import li.gkd.app.util.launchLogged

private var nextUpdateSubsTime = 0L
private var autoRefreshPending = false

fun refreshSubscriptionsOnLauncher() {
    val interval = settings.value.updateSubsInterval
    if (interval <= 0 || autoRefreshPending) return
    val currentTime = System.currentTimeMillis()
    if (currentTime < nextUpdateSubsTime) return
    autoRefreshPending = true
    appScope.launchLogged {
        try {
            val result = SubscriptionRepository.refresh()
            if (result !is SubscriptionResult.Busy) {
                nextUpdateSubsTime =
                    currentTime +
                        if (
                            result is SubscriptionResult.Failure &&
                                result.reason == SubscriptionResult.FailureReason.NetworkUnavailable
                        ) {
                            5 * 60_000L
                        } else {
                            interval.coerceAtLeast(UpdateTimeOption.Everyday.value)
                        }
            }
        } finally {
            autoRefreshPending = false
        }
    }
}
