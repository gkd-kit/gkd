package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.destinations.AppItemPageDestination
import javax.inject.Inject

@HiltViewModel
class AppItemVm @Inject constructor(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = AppItemPageDestination.argsFrom(stateHandle)

    val subsConfigsFlow = DbSet.subsConfigDao.queryGroupTypeConfig(args.subsItemId, args.appId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subsAppFlow = MutableStateFlow<SubscriptionRaw.AppRaw?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val subscriptionRaw = SubsItem.getSubscriptionRaw(args.subsItemId) ?: return@launch
            subsAppFlow.value = subscriptionRaw.apps.find { it.id == args.appId }
        }
    }


}