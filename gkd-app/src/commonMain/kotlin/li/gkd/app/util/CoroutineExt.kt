package li.gkd.app.util

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import li.gkd.app.logging.LogUtils
import li.songe.codeorigin.CallSite

fun CoroutineScope.launchLogged(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    @CallSite loc: String = "",
    block: suspend CoroutineScope.() -> Unit,
) =
    launch(context, start) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.e(e, loc = loc)
        }
    }
