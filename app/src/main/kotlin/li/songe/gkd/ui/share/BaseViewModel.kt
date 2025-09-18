package li.songe.gkd.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.util.subsMapFlow


abstract class BaseViewModel : ViewModel() {
    private val countFlow by lazy { MutableStateFlow(0) }
    val firstLoadingFlow by lazy { countFlow.mapNew { it > 0 } }
    fun <T> Flow<T>.attachLoad(): Flow<T> {
        countFlow.update { it + 1 }
        var currentUsed = false
        return onEach {
            if (!currentUsed) {
                countFlow.update {
                    if (!currentUsed) {
                        currentUsed = true
                        it - 1
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun <T> Flow<T>.stateInit(initialValue: T): StateFlow<T> {
        return stateIn(viewModelScope, SharingStarted.Eagerly, initialValue)
    }

    fun <T> Flow<T>.launchCollect(collector: FlowCollector<T>) {
        viewModelScope.launch { collect(collector) }
    }

    fun <T> StateFlow<T>.launchOnChange(collector: FlowCollector<T>) {
        viewModelScope.launch { drop(1).collect(collector) }
    }

    fun <T, M> StateFlow<T>.mapNew(
        mapper: (value: T) -> M,
    ): StateFlow<M> = map { mapper(it) }.stateIn(
        viewModelScope, SharingStarted.Eagerly, mapper(value)
    )

    fun mapSafeSubs(id: Long): StateFlow<RawSubscription> {
        return subsMapFlow.mapNew {
            it[id] ?: RawSubscription(
                id = id,
                version = 0,
                name = id.toString()
            )
        }
    }

}
