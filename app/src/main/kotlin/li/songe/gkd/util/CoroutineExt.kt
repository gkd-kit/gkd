package li.songe.gkd.util

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

fun CoroutineScope.launchTry(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    silent: Boolean = false,
    block: suspend CoroutineScope.() -> Unit,
) = launch(context, start) {
    try {
        block()
    } catch (e: CancellationException) {
        e.printStackTrace()
    } catch (_: InterruptRuleMatchException) {
    } catch (e: Throwable) {
        e.printStackTrace()
        LogUtils.d(e)
        if (!silent) {
            toast(e.message ?: e.stackTraceToString())
        }
    }
}

@Composable
fun CoroutineScope.launchAsFn(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): () -> Unit = {
    launch(context, start) {
        try {
            block()
        } catch (e: CancellationException) {
            e.printStackTrace()
        } catch (e: Throwable) {
            e.printStackTrace()
            toast(e.message ?: e.stackTraceToString())
        }
    }
}

@Composable
fun <T> CoroutineScope.launchAsFn(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.(T) -> Unit,
): (T) -> Unit = {
    launch(context, start) {
        try {
            block(it)
        } catch (e: CancellationException) {
            e.printStackTrace()
        } catch (e: Throwable) {
            e.printStackTrace()
            toast(e.message ?: e.stackTraceToString())
        }
    }
}

suspend fun stopCoroutine(): Nothing {
    coroutineContext[Job]?.cancel()
    yield()
    // the following code will not be run
    throw CancellationException("Coroutine stopped")
}
