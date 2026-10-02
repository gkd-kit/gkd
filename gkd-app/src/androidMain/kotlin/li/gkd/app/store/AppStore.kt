package li.gkd.app.store

import li.gkd.app.resources.Res
import li.gkd.app.resources.rule_matching_enable
import li.gkd.app.resources.rule_matching_pause
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ToastUtils

object AppStore {

    fun toggleEnableMatch() {
        if (SettingsRepository.settings.value.enableMatch) {
            ToastUtils.show(Res.string.rule_matching_pause.getSync())
        } else {
            ToastUtils.show(Res.string.rule_matching_enable.getSync())
        }
        SettingsRepository.updateSettings { it.copy(enableMatch = !it.enableMatch) }
    }

    fun updateEnableAutomator(value: Boolean) {
        if (value == SettingsRepository.settings.value.enableAutomator) return
        SettingsRepository.updateSettings { it.copy(enableAutomator = value) }
    }

}
