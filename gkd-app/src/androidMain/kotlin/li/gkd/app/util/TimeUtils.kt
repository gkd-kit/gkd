package li.gkd.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

data class ThrottleTimer(
    private val interval: Long = 500L,
) {
    private var lastAccessTime: Long = 0L
    fun expired(): Boolean {
        val t = System.currentTimeMillis()
        if (t - lastAccessTime > interval) {
            lastAccessTime = t
            return true
        }
        return false
    }
}

object TimeUtils {
    @Composable
    fun throttle(
        fn: (() -> Unit),
    ): (() -> Unit) {
        val timer = remember { ThrottleTimer() }
        return remember(fn) {
            {
                if (timer.expired()) {
                    fn.invoke()
                }
            }
        }
    }

    @Composable
    fun <T> throttle(
        fn: ((T) -> Unit),
    ): ((T) -> Unit) {
        val timer = remember { ThrottleTimer() }
        return remember(fn) {
            {
                if (timer.expired()) {
                    fn.invoke(it)
                }
            }
        }
    }
}
