package li.gkd.app.service

import android.provider.Settings
import com.google.android.accessibility.selecttospeak.SelectToSpeakServiceCompanion
import li.gkd.app.app
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.input.topTaskAppId
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.settings.AppScopePolicy
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.util.componentName

object CompanionServiceController {
    private val component by lazy { SelectToSpeakServiceCompanion::class.componentName }

    @Volatile private var requested = false
    @Volatile private var instance: SelectToSpeakServiceCompanion? = null

    fun onConnected(service: SelectToSpeakServiceCompanion) {
        if (!requested) {
            LogUtils.i("Companion connected without an enable request, disabling")
            service.disableSelf()
            return
        }
        instance = service
        LogUtils.i("Companion connected")
    }

    fun onDisconnected(service: SelectToSpeakServiceCompanion) {
        if (instance === service) {
            instance = null
            LogUtils.i("Companion disconnected")
        }
    }

    fun update() = launchServiceControl {
        val settings = SettingsRepository.settings.value
        val appId = topTaskAppId
        val stopReason =
            when {
                !settings.enableAutomator -> "automator_disabled"
                !A11yService.isRunning.value && uiAutomationFlow.value == null ->
                    "no_automator_session"
                AppScopePolicy.blocksCompanion(
                    settings,
                    SettingsRepository.companionScopeAppList.value,
                    appId,
                ) -> "app_scope"
                else -> null
            }
        val shouldRun = stopReason == null
        if (!shouldRun) {
            requested = false
            instance?.let {
                LogUtils.i("Companion disable requested: reason=$stopReason, appId=$appId")
                it.disableSelf()
                return@launchServiceControl
            }
        }
        if (!PermissionStates.writeSecureSettings.stateFlow.value) return@launchServiceControl
        val names = app.getSecureA11yServices()
        val changed = if (shouldRun) names.add(component) else names.remove(component)
        if (shouldRun) {
            val wasRequested = requested
            requested = true
            if (!wasRequested || changed) {
                LogUtils.i("Companion enable requested: appId=$appId, alreadyListed=${!changed}")
            }
        } else if (changed) {
            LogUtils.i("Companion disable requested: reason=$stopReason, appId=$appId")
        }
        if (changed) {
            if (shouldRun) app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            app.putSecureA11yServices(names)
        }
    }
}
