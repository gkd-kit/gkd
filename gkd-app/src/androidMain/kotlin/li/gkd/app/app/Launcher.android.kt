package li.gkd.app.app

import kotlinx.coroutines.flow.StateFlow

actual val launcherAppIdFlow: StateFlow<String>
    get() = li.gkd.app.a11y.launcherAppIdFlow
