package li.songe.gkd.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ThrottleState(
    private val scope: CoroutineScope,
    private val miniAwaitTime: Long = 200L,
    val loading: Boolean = false,
    private val onChangeLoading: (value: Boolean) -> Unit = {},
) {
    companion object {
        private lateinit var defaultFalseInstance: ThrottleState

        @Composable
        fun use(scope: CoroutineScope, miniAwaitTime: Long = 0): ThrottleState {
            var loading by remember { mutableStateOf(false) }
            if (loading) {
                if (!::defaultFalseInstance.isInitialized) {
                    defaultFalseInstance = ThrottleState(scope, miniAwaitTime, loading = true)
                }
                return defaultFalseInstance
            }
            return ThrottleState(scope, miniAwaitTime, loading = false) {
                loading = it
            }
        }
    }

    class CatchInvoke(
        private val onChangeCatch: (catchFn: ((e: Exception) -> Unit)) -> Unit,
        private val fn: () -> Unit,
    ) : () -> Unit {
        override fun invoke() {
            fn()
        }

        fun catch(catchFn: ((e: Exception) -> Unit)): CatchInvoke {
            onChangeCatch(catchFn)
            return this
        }
    }

    class CatchInvoke1<T>(
        private val onChangeCatch: (catchFn: ((e: Exception) -> Unit)) -> Unit,
        private val fn: (T) -> Unit,
    ) : (T) -> Unit {
        override fun invoke(t: T) {
            fn(t)
        }

        fun catch(catchFn: ((e: Exception) -> Unit)): CatchInvoke1<T> {
            onChangeCatch(catchFn)
            return this
        }
    }

    fun invoke(
        miniAwaitTime: Long = this.miniAwaitTime,
        fn: suspend () -> Unit,
    ): CatchInvoke {
        var catchFn = { e: Exception -> e.printStackTrace() }
        return CatchInvoke({ catchFn = it }) fnWrapper@{
            if (loading) return@fnWrapper
            onChangeLoading(true)
            scope.launch {
                try {
                    fn()
                } catch (e: Exception) {
                    catchFn(e)
                } finally {
                    delay(miniAwaitTime)
                    onChangeLoading(false)
                }
            }

        }
    }

    fun <T> invoke(
        miniAwaitTime: Long = this.miniAwaitTime,
        fn: suspend (T) -> Unit,
    ): CatchInvoke1<T> {
        var catchFn = { e: Exception -> e.printStackTrace() }
        return CatchInvoke1({ catchFn = it }) fnWrapper@{ t ->
            if (loading) return@fnWrapper
            onChangeLoading(true)
            scope.launch {
                try {
                    fn(t)
                } catch (e: Exception) {
                    catchFn(e)
                } finally {
                    delay(miniAwaitTime)
                    onChangeLoading(false)
                }
            }

        }
    }


}