package li.gkd.app.priv

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import li.gkd.app.appScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.util.launchLogged
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeConfig

object PrivilegeOwnerLifecycle {
    private const val FOLLOW_DEATH_DELAY_MILLIS = 60 * 60 * 1000L
    private const val APP_RESTART_PASSIVE_TIMEOUT_MILLIS = 5_000L
    private var automatorEnabled = false
    private var updateInstallationActive = false

    fun initialize() {
        appScope.launchLogged(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            SettingsRepository.settings
                .map { it.enableAutomator }
                .distinctUntilChanged()
                .collect(::setAutomatorEnabled)
        }
    }

    @Synchronized
    private fun setAutomatorEnabled(enableAutomator: Boolean) {
        automatorEnabled = enableAutomator
        applyConfiguration()
    }

    @Synchronized
    fun setUpdateInstallationActive(active: Boolean) {
        updateInstallationActive = active
        applyConfiguration()
    }

    private fun applyConfiguration() {
        PrivilegeConfig.configure(
            followDeathDelayMillis = FOLLOW_DEATH_DELAY_MILLIS,
            // Only the install-success callback may relaunch the app during an update.
            activeReconnectOnOwnerDeath = automatorEnabled && !updateInstallationActive,
        )
    }

    fun prepareAppRestart() {
        runCatching {
            Privilege.prepareOwnerRestart(APP_RESTART_PASSIVE_TIMEOUT_MILLIS)
        }
            .onFailure { error ->
                LogUtils.e("prepare owner restart failed", error)
            }
    }
}
