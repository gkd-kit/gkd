package li.songe.gkd.util


import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


private val emptyFn = {}
private val emptyFn2 = { _: Boolean -> }
private val emptyFnT1 = { _: Any? -> }

data class TaskState(
    private val scope: CoroutineScope,
    val loading: Boolean = false,
    private val miniInterval: Long = 0,
    private var innerLoading: Boolean = false,
    val onChangeLoading: (Boolean) -> Unit = emptyFn2,
) {
    fun launchAsFn(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend TaskState.() -> Unit,
    ): () -> Unit {
        if (loading) return emptyFn
        return scope.launchAsFn(context, start) {
            if (innerLoading) return@launchAsFn
            innerLoading = true
            onChangeLoading(true)
            val start = System.currentTimeMillis()
            try {
                try {
                    block()
                } finally {
                    val end = System.currentTimeMillis()
                    delay(miniInterval - (end - start))
                    onChangeLoading(false)
                    innerLoading = false
                }
            } catch (_: CancellationException) {
            }
        }
    }

    fun <T> launchAsFn(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend TaskState.(T) -> Unit,
    ): (T) -> Unit {
        if (loading) return emptyFnT1
        return scope.launchAsFn<T>(context, start) {
            if (innerLoading) return@launchAsFn
            innerLoading = true
            onChangeLoading(true)
            val start = System.currentTimeMillis()
            try {
                try {
                    block(it)
                } finally {
                    val end = System.currentTimeMillis()
                    delay(miniInterval - (end - start))
                    onChangeLoading(false)
                    innerLoading = false
                }
            } catch (_: CancellationException) {

            }
        }
    }
}

@Composable
fun CoroutineScope.useTask(miniInterval: Long = 1500L, dialog: Boolean = false): TaskState {
    val trueTask: TaskState = remember {
        TaskState(scope = this, loading = true)
    }
    lateinit var task: MutableState<TaskState>
    lateinit var falseTask: TaskState
    falseTask = remember {
        TaskState(scope = this, miniInterval = miniInterval) {
            task.value = if (it) trueTask else falseTask
        }
    }
    task = remember { mutableStateOf(falseTask) }
    if (dialog && task.value.loading) {
        Dialog(
            onDismissRequest = {},
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(100.dp)
                    .padding(10.dp),
            )
        }
    }
    return task.value
}