package li.gkd.app.settings

import kotlinx.coroutines.flow.MutableStateFlow

expect fun defaultSettings(): SettingsStore

expect fun defaultBlockMatchAppList(): Set<String>

expect fun createTermsAcceptedFlow(storage: SettingsStorage): MutableStateFlow<Boolean>
