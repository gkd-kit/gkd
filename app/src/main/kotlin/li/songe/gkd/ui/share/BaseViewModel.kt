package li.songe.gkd.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import li.songe.gkd.util.mapState


abstract class BaseViewModel : ViewModel() {
    private val countFlow by lazy { MutableStateFlow(0) }
    val firstLoadingFlow by lazy { countFlow.mapState(viewModelScope) { it > 0 } }
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
}
