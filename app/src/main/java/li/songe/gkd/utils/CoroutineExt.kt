package li.songe.gkd.utils

import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext


fun CoroutineScope.launchWhile(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
) = launch(context, start) {
    while (isActive) {
        block()
    }
}


fun CoroutineScope.launchWhileTry(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    interval: Long = 0,
    block: suspend CoroutineScope.() -> Unit,
) = launch(context, start) {
    while (isActive) {
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        delay(interval)
    }
}

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
        ToastUtils.showShort(e.message)
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
                ToastUtils.showShort(e.message)
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
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort(e.message)
            }
        }
    }
}

suspend fun stopJob(): Nothing {
    coroutineContext[Job]?.cancel()
    delay(1)
    error("stop failed")
}

