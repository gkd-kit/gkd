package li.songe.gkd.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

open class ViewModelExt : ViewModel() {
    fun <T> Flow<T>.stateInit(initialValue: T): StateFlow<T> {
        return stateIn(viewModelScope, SharingStarted.Eagerly, initialValue)
    }
}
