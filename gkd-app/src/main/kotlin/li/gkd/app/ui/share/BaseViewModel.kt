package li.songe.gkd.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface Loadable<out T : Any> {
    val value: T?

    data object Loading : Loadable<Nothing> {
        override val value: Nothing? = null
    }

    data class Ready<T : Any>(override val value: T) : Loadable<T>

    data class Failure(val cause: Throwable) : Loadable<Nothing> {
        override val value: Nothing? = null
    }
}

abstract class BaseViewModel : ViewModel() {
    open val scope get() = viewModelScope

    fun <T> Flow<T>.stateInit(initialValue: T): StateFlow<T> {
        return stateIn(scope, SharingStarted.Eagerly, initialValue)
    }

    fun <T : Any> Flow<T>.stateLoadable(): StateFlow<Loadable<T>> {
        return map<T, Loadable<T>> { Loadable.Ready(it) }
            .catch { emit(Loadable.Failure(it)) }
            .stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)
    }

    protected fun requiredSubscription(id: Long) =
        RequiredSubscription(id, scope)

    fun <T, M> StateFlow<T>.mapNew(
        mapper: (value: T) -> M,
    ): StateFlow<M> = map { mapper(it) }.stateIn(
        scope, SharingStarted.Eagerly, mapper(value)
    )

}
