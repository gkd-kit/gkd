package li.gkd.app.ui.share

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.data.RawSubscription
import li.gkd.app.util.LoadedSubscription
import li.gkd.app.util.SubscriptionSnapshot
import li.gkd.app.util.SubscriptionStore

class RequiredSubscription(
    private val id: Long,
    private val scope: CoroutineScope,
) {
    private fun loadableState(
        state: Loadable<SubscriptionSnapshot>,
    ): Loadable<LoadedSubscription> {
        val snapshot = when (state) {
            Loadable.Loading -> return Loadable.Loading
            is Loadable.Failure -> return state
            is Loadable.Ready -> state.value
        }
        val subscription = snapshot.subscriptions[id]
        return if (subscription != null) {
            Loadable.Ready(
                LoadedSubscription(
                    value = subscription,
                    updateError = snapshot.updateErrors[id],
                ),
            )
        } else {
            Loadable.Failure(
                snapshot.loadErrors[id]
                    ?: snapshot.updateErrors[id]
                    ?: IllegalStateException("订阅不存在: $id"),
            )
        }
    }

    val state: StateFlow<Loadable<LoadedSubscription>> =
        SubscriptionStore.snapshotFlow.map(::loadableState).stateIn(
            scope,
            SharingStarted.Eagerly,
            loadableState(SubscriptionStore.snapshotFlow.value),
        )

    fun requireValue(): RawSubscription {
        return when (val current = state.value) {
            Loadable.Loading -> error("订阅尚未加载: $id")
            is Loadable.Failure -> throw current.cause
            is Loadable.Ready -> current.value.value
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T : Any> buildUiState(
        initialValue: ((RawSubscription) -> T)? = null,
        source: (RawSubscription) -> Flow<T>,
    ): StateFlow<Loadable<T>> {
        val initialState = when (val current = state.value) {
            Loadable.Loading -> Loadable.Loading
            is Loadable.Failure -> current
            is Loadable.Ready -> initialValue?.let {
                runCatching { it(current.value.value) }.fold(
                    onSuccess = { value -> Loadable.Ready(value) },
                    onFailure = { error -> Loadable.Failure(error) },
                )
            } ?: Loadable.Loading
        }
        return state.flatMapLatest { current ->
            when (current) {
                Loadable.Loading -> flowOf(Loadable.Loading)
                is Loadable.Failure -> flowOf(current)
                is Loadable.Ready -> flow {
                    emitAll(source(current.value.value))
                }.map<T, Loadable<T>> { Loadable.Ready(it) }
                    .catch { emit(Loadable.Failure(it)) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, initialState)
    }

    suspend fun update(
        transform: (RawSubscription) -> RawSubscription,
    ): Boolean = SubscriptionStore.update(id, transform)
}
