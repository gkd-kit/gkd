package li.gkd.app.settings

expect fun defaultSettings(): SettingsStore
expect fun defaultBlockMatchAppList(): Set<String>
expect fun defaultTermsAccepted(): Boolean
