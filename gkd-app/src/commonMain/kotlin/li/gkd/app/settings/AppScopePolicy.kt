package li.gkd.app.settings

object AppScopePolicy {
    fun blocksCompanion(
        settings: SettingsStore,
        companionScope: Set<String>,
        appId: String,
    ): Boolean = settings.useAutomation && appId in companionScope

    fun blockedA11y(
        settings: SettingsStore,
        blockedMatch: Set<String>,
        blockedA11y: Set<String>,
    ): Set<String> = if (settings.blockA11yAppListFollowMatch) blockedMatch else blockedA11y

    fun blocksMatch(
        settings: SettingsStore,
        blockedMatch: Set<String>,
        blockedA11y: Set<String>,
        appId: String,
    ): Boolean =
        appId in blockedMatch ||
            (settings.enableBlockA11yAppList &&
                appId in
                    blockedA11y(
                        settings,
                        blockedMatch,
                        blockedA11y,
                    ))
}
