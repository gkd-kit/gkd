package li.gkd.app.settings

object AppScopePolicy {
    fun blockedA11y(
        settings: SettingsStore,
        blockedMatch: Set<String>,
        blockedA11y: Set<String>
    ): Set<String> =
        if (settings.blockA11yAppListFollowMatch) blockedMatch else blockedA11y

    fun a11yScope(settings: SettingsStore, scope: Set<String>): Set<String> =
        if (settings.useAutomation) scope else emptySet()

    fun blocksMatch(
        settings: SettingsStore,
        blockedMatch: Set<String>,
        blockedA11y: Set<String>,
        appId: String
    ): Boolean =
        appId in blockedMatch || (settings.enableBlockA11yAppList && appId in blockedA11y(
            settings,
            blockedMatch,
            blockedA11y
        ))
}
