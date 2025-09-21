package li.songe.gkd.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update


@Composable
fun <T> MutableStateFlow<T>.asMutableState(): MutableState<T> {
    val state = collectAsState()
    return remember(this) {
        val stateFlow = this
        object : MutableState<T> {
            val setter: (T) -> Unit = { stateFlow.value = it }
            override var value: T
                get() = state.value
                set(newValue) = setter(newValue)

            override fun component1() = value
            override fun component2() = setter
        }
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
fun <T, S> MutableStateFlow<T>.asMutableStateFlow(
    getter: (T) -> S,
    setter: (S) -> T
) = object : MutableStateFlow<S> {
    val source = this@asMutableStateFlow
    override var value: S
        get() = getter(source.value)
        set(newValue) = source.update { setter(newValue) }

    override fun compareAndSet(expect: S, update: S) = source.compareAndSet(
        setter(expect),
        setter(update),
    )

    override suspend fun collect(collector: FlowCollector<S>): Nothing {
        var oldValue = value
        collector.emit(oldValue)
        source.collect {
            val newValue = getter(it)
            if (oldValue != newValue) {
                oldValue = newValue
                collector.emit(oldValue)
            }
        }
    }

    override val replayCache get() = source.replayCache.map(getter)
    override val subscriptionCount get() = source.subscriptionCount
    override suspend fun emit(value: S) = source.emit(setter(value))
    override fun tryEmit(value: S) = source.tryEmit(setter(value))
    override fun resetReplayCache() = source.resetReplayCache()
}
