package li.songe.gkd.router

import androidx.compose.runtime.compositionLocalOf
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Router<R>(
    private val getStack: () -> List<Pair<Page<*, *>, *>>,
    private val setStack: (List<Pair<Page<*, *>, *>>) -> Unit,
    private val scope: CoroutineScope,
) {
    private var stack: List<Pair<Page<*, *>, *>>
        private set(value) = setStack(value)
        get() = getStack()

    fun navigate(page: Page<*, *>) {
        stack = stack.toMutableList().apply { add(page to page.defaultParams) }
    }

    fun <P, R> navigate(page: Page<P, R>, params: P) {
        stack = stack.toMutableList().apply { add(page to params) }
    }


    private val resultCallbackStack = mutableListOf<Pair<Int, Continuation<*>>>()

    /**
     * 等待时间包含了页面过度动画
     */
    suspend fun <P, R> navigateForResult(page: Page<P, R>): R = suspendCoroutine { c ->
        resultCallbackStack.add((stack.size + 1) to c)
        navigate(page)
    }

    /**
     * 等待时间包含了页面过度动画
     */
    suspend fun <P, R> navigateForResult(page: Page<P, R>, params: P): R = suspendCoroutine { c ->
        resultCallbackStack.add((stack.size + 1) to c)
        navigate(page, params)
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack = stack.subList(0, stack.size - 1)
        return true
    }

    fun back(result: R) {
        if (!back()) return
        val callback = resultCallbackStack.lastOrNull()
        if (callback?.first == stack.size) {
            resultCallbackStack.removeLast()
//            TODO
            scope.launch {
                delay(pageTransitionDurationMillis.toLong())
                (callback.second as Continuation<R>).resume(result)
            }
        }

    }

    companion object {
        val LocalRouter = compositionLocalOf<Router<*>> { error("not found Router") }
    }
}