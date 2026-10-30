package li.gkd.app.matching.diagnostics

import li.gkd.app.matching.AndroidAutomationSessions
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.input.isInteractive
import li.gkd.app.util.ScreenUtils

actual fun matchForegroundSnapshot(): ForegroundDebugState? {
    val service = AndroidAutomationSessions.service
    return ForegroundDebugState(
        isInteractive,
        ScreenUtils.isLocked(),
        service?.mode?.value,
        AndroidForegroundState.foregroundSnapshot(),
        service?.let(AndroidAutomationSessions::isEffective) == true,
        service?.subscribedEventTypes,
    )
}
