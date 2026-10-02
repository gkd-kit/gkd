package li.gkd.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.state.Loadable

abstract class BaseViewModel : ViewModel() {
    val scope get() = viewModelScope

    fun <T> Flow<T>.stateInit(initialValue: T): StateFlow<T> {
        return stateIn(scope, SharingStarted.Eagerly, initialValue)
    }

    fun <T : Any> Flow<T>.stateLoadable(): StateFlow<Loadable<T>> {
        return map<T, Loadable<T>> { Loadable.Ready(it) }
            .catch { emit(Loadable.Failure(it)) }
            .stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)
    }


}
