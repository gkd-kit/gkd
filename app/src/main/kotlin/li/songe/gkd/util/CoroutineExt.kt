package li.songe.gkd.util

import com.blankj.utilcode.util.LogUtils
import com.hjq.toast.Toaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun CoroutineScope.launchTry(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
) = launch(context, start) {
    try {
        block()
    } catch (e: CancellationException) {
        e.printStackTrace()
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d(e)
        Toaster.show(e.message ?: e.stackTraceToString())
    }
}

fun CoroutineScope.launchAsFn(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): () -> Unit {
    return {
        launch(context, start) {
            try {
                block()
            } catch (e: CancellationException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
                Toaster.show(e.message ?: e.stackTraceToString())
            }
        }
    }
}

fun <T> CoroutineScope.launchAsFn(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.(T) -> Unit,
): (T) -> Unit {
    return {
        launch(context, start) {
            try {
                block(it)
            } catch (e: CancellationException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
                Toaster.show(e.message ?: e.stackTraceToString())
            }
        }
    }
}

