package li.gkd.app.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.provider.Settings
import com.google.android.accessibility.selecttospeak.SelectToSpeakServiceCompanion
import com.hjq.device.compat.DeviceBrand
import li.gkd.app.app
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
            service.disableSelf()
            return
        }
        configureFeedbackForDevice(service)
        instance = service
    }

    private fun configureFeedbackForDevice(service: SelectToSpeakServiceCompanion) {
        val feedbackType =
            when {
                DeviceBrand.isXiaoMi() || DeviceBrand.isRedMi() ->
                    // One feedback bit from HyperOS's cleanup mask is sufficient.
                    AccessibilityServiceInfo.FEEDBACK_GENERIC or
                        AccessibilityServiceInfo.FEEDBACK_HAPTIC
                else -> return
            }
        val info = service.serviceInfo ?: return
        info.feedbackType = feedbackType
        service.serviceInfo = info
    }

    fun onDisconnected(service: SelectToSpeakServiceCompanion) {
        if (instance === service) {
            instance = null
        }
    }

    fun update() = launchServiceControl {
        val settings = SettingsRepository.settings.value
        val shouldRun =
            settings.enableAutomator &&
                (A11yService.isRunning.value || uiAutomationFlow.value != null) &&
                !AppScopePolicy.blocksCompanion(
                    settings,
                    SettingsRepository.companionScopeAppList.value,
                    topTaskAppId,
                )
        if (!shouldRun) {
            requested = false
            instance?.let {
                it.disableSelf()
                return@launchServiceControl
            }
        }
        if (!PermissionStates.writeSecureSettings.stateFlow.value) return@launchServiceControl
        val names = app.getSecureA11yServices()
        val changed = if (shouldRun) names.add(component) else names.remove(component)
        if (shouldRun) requested = true
        if (changed) {
            if (shouldRun) app.putSecureInt(Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            app.putSecureA11yServices(names)
        }
    }
}
