package li.gkd.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.app.appScope
import li.gkd.app.ui.share.launchUi
import li.songe.codeorigin.CallSite

private val serviceControlMutex = Mutex()

suspend fun <T> withServiceControl(block: suspend () -> T): T =
    withContext(Dispatchers.IO) { serviceControlMutex.withLock { block() } }

fun launchServiceControl(@CallSite loc: String = "", block: suspend () -> Unit) {
    appScope.launchUi(loc = loc) {
        withServiceControl(block)
    }
}
