package li.songe.gkd.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object CoroutineUtil {
    @Composable
    inline fun <P0> CoroutineScope.throttle(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        crossinline block: suspend CoroutineScope.(p0: P0) -> Unit
    ): (p0: P0) -> Unit {
        var running = false
        val func: (p0: P0) -> Unit = f@{ p0 ->
            if (running) {
                return@f
            }
            launch(context = context, start = start) {
                running = true
                try {
                    block.invoke(this, p0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                running = false
            }
        }
        return remember { func }
    }

    @Composable
    inline fun <P0, P1> CoroutineScope.throttle(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        crossinline block: suspend CoroutineScope.(p0: P0, p1: P1) -> Unit
    ): (p0: P0, p1: P1) -> Unit {
        var running = false
        val func: (p0: P0, p1: P1) -> Unit = f@{ p0, p1 ->
            if (running) {
                return@f
            }
            launch(context = context, start = start) {
                running = true
                try {
                    block.invoke(this, p0, p1)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                running = false
            }
        }
        return remember { func }
    }

    @Composable
    inline fun <P0, P1, P2> CoroutineScope.throttle(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        crossinline block: suspend CoroutineScope.(p0: P0, p1: P1, p2: P2) -> Unit
    ): (p0: P0, p1: P1, p2: P2) -> Unit {
        var running = false
        val func: (p0: P0, p1: P1, p2: P2) -> Unit = f@{ p0, p1, p2 ->
            if (running) {
                return@f
            }
            launch(context = context, start = start) {
                running = true
                try {
                    block.invoke(this, p0, p1, p2)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                running = false
            }
        }
        return remember { func }
    }
}