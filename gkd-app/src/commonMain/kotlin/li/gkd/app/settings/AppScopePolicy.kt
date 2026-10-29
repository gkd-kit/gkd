package li.gkd.app.settings

object AppScopePolicy {
    fun usesA11y(
        settings: SettingsStore,
        a11yScope: Set<String>,
        automationScope: Set<String>,
        appId: String,
    ): Boolean = if (settings.useA11y) appId !in automationScope else appId in a11yScope

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
