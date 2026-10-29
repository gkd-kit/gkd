package li.gkd.app.platform.service

import li.gkd.app.service.ActivityService
import li.gkd.app.service.ButtonService
import li.gkd.app.service.DebugService
import li.gkd.app.service.EventService
import li.gkd.app.service.StatusService
import li.gkd.app.settings.SettingsRepository

object ServiceController {
    fun setDebugEnabled(enabled: Boolean) =
        setEnabled(
            enabled = enabled,
            start = DebugService::start,
            stop = DebugService::stop,
        )

    fun setSnapshotButtonEnabled(enabled: Boolean) =
        setEnabled(
            enabled = enabled,
            start = ButtonService::start,
            stop = ButtonService::stop,
        )

    fun setActivityMonitorEnabled(enabled: Boolean) =
        setEnabled(
            enabled = enabled,
            start = ActivityService::start,
            stop = ActivityService::stop,
        )

    fun setEventMonitorEnabled(enabled: Boolean) =
        setEnabled(
            enabled = enabled,
            start = EventService::start,
            stop = EventService::stop,
        )

    fun setStatusEnabled(enabled: Boolean) {
        setEnabled(
            enabled = enabled,
            start = StatusService::start,
            stop = StatusService::stop,
        )
        SettingsRepository.updateSettings { it.copy(enableStatusService = enabled) }
    }

    private fun setEnabled(
        enabled: Boolean,
        start: () -> Unit,
        stop: () -> Unit,
    ) {
        if (enabled) start() else stop()
    }
}
