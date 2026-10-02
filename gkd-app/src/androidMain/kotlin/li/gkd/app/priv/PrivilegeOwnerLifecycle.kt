package li.gkd.app.priv

import li.gkd.app.util.LogUtils
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeConfig

object PrivilegeOwnerLifecycle {
    private const val FOLLOW_DEATH_DELAY_MILLIS = 10 * 60 * 1000L
    private const val APP_RESTART_PASSIVE_TIMEOUT_MILLIS = 5_000L

    fun configure(enableAutomator: Boolean) {
        PrivilegeConfig.configure(
            followDeathDelayMillis = if (enableAutomator) FOLLOW_DEATH_DELAY_MILLIS else 0L,
            activeReconnectOnOwnerDeath = enableAutomator,
        )
    }

    fun prepareAppRestart() {
        runCatching {
            Privilege.prepareOwnerRestart(APP_RESTART_PASSIVE_TIMEOUT_MILLIS)
        }.onFailure { error ->
            LogUtils.d("prepare owner restart failed", error)
        }
    }
}
