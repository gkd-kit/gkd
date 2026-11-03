package li.gkd.app.platform

/** Requests a restart after automation settings change; success means the request was scheduled. */
expect fun requestAutomatorRestart(): PlatformResult<Unit>

/** Requests only companion synchronization; success means the request was scheduled. */
expect fun requestCompanionServiceUpdate(): PlatformResult<Unit>
