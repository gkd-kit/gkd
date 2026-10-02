package li.gkd.app.app

import kotlinx.coroutines.CoroutineScope
import li.gkd.app.appScope

actual fun applicationScope(): CoroutineScope = appScope
