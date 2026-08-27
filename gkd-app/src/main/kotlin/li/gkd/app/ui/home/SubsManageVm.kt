package li.gkd.app.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsItem
import li.gkd.db.Db
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.Loadable
import li.gkd.app.util.SubscriptionResult
import li.gkd.app.util.SubscriptionSnapshot
import li.gkd.app.util.SubscriptionStore
import li.gkd.app.util.launchTry
import li.gkd.app.util.toast

data class SubsManageUiState(
    val subItems: List<SubsItem>,
    val subscriptions: Map<Long, RawSubscription>,
    val refreshing: Boolean,
    val loadErrors: Map<Long, Exception>,
    val refreshErrors: Map<Long, Exception>,
)

private fun buildSubsManageUiState(
    subItems: List<SubsItem>,
    snapshot: SubscriptionSnapshot,
    refreshing: Boolean,
) = SubsManageUiState(
    subItems = subItems,
    subscriptions = snapshot.subscriptions,
    refreshing = refreshing,
    loadErrors = snapshot.loadErrors,
    refreshErrors = snapshot.updateErrors,
)

class SubsManageVm : BaseViewModel() {
    val settingsDialogVisibleFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val powerWarningItemFlow: StateFlow<SubsItem?>
        field = MutableStateFlow(null)

    val uiState: StateFlow<Loadable<SubsManageUiState>> =
        SubscriptionStore.snapshotFlow.flatMapLatest { snapshotState ->
            when (snapshotState) {
                Loadable.Loading -> flowOf(Loadable.Loading)
                is Loadable.Failure -> flowOf(snapshotState)
                is Loadable.Ready -> combine(
                    Db.subsItemDao.query(),
                    SubscriptionStore.updating,
                ) { subItems, refreshing ->
                    buildSubsManageUiState(
                        subItems = subItems,
                        snapshot = snapshotState.value,
                        refreshing = refreshing,
                    )
                }.map<SubsManageUiState, Loadable<SubsManageUiState>> { Loadable.Ready(it) }
                    .catch { emit(Loadable.Failure(it)) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)

    fun setUpdateInterval(value: Long) {
        storeFlow.update { it.copy(updateSubsInterval = value) }
    }

    fun setPowerWarningEnabled(enabled: Boolean) {
        storeFlow.update { it.copy(subsPowerWarn = enabled) }
    }

    fun setSettingsDialogVisible(visible: Boolean) {
        settingsDialogVisibleFlow.value = visible
    }

    fun toggleMatching() {
        storeFlow.update { it.copy(enableMatch = !it.enableMatch) }
    }

    fun refresh() {
        scope.launchTry(Dispatchers.IO) {
            SubscriptionStore.refresh().message?.let { toast(it) }
        }
    }

    suspend fun deleteSubscriptions(ids: Set<Long>): SubscriptionResult =
        SubscriptionStore.delete(*ids.toLongArray())

    fun updateOrder(items: List<SubsItem>) {
        scope.launchTry(Dispatchers.IO) {
            Db.subsItemDao.batchUpdateOrder(items)
        }
    }

    private fun shouldWarnBeforeEnabling(item: SubsItem): Boolean {
        val state = uiState.value.value ?: return false
        return storeFlow.value.subsPowerWarn &&
            !item.isLocal &&
            state.subItems.any { current ->
                current.id != item.id &&
                    current.enable &&
                    !current.isLocal &&
                    state.subscriptions[current.id]?.hasRule != false
            }
    }

    fun requestSubscriptionEnabled(item: SubsItem, enabled: Boolean) {
        if (enabled && shouldWarnBeforeEnabling(item)) {
            powerWarningItemFlow.value = item
        } else {
            setSubscriptionEnabled(item, enabled)
        }
    }

    fun dismissPowerWarning() {
        powerWarningItemFlow.value = null
    }

    fun confirmPowerWarning() {
        val item = powerWarningItemFlow.value ?: return
        powerWarningItemFlow.value = null
        setSubscriptionEnabled(item, true)
    }

    private fun setSubscriptionEnabled(item: SubsItem, enabled: Boolean) {
        scope.launchTry(Dispatchers.IO) {
            Db.subsItemDao.updateEnable(item.id, enabled)
        }
    }

    suspend fun addOrModifySubscription(
        url: String,
        oldItem: SubsItem? = null,
    ): SubscriptionResult = SubscriptionStore.addOrModifyRemote(url, oldItem)
}
