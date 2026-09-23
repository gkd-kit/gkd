package li.gkd.app.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsItem
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore
import li.gkd.app.util.MutexState
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.subscription.SubscriptionResult
import li.gkd.app.data.subscription.SubscriptionSnapshot
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.message
import li.gkd.app.util.ToastUtils.toast
import li.gkd.db.Db
import li.gkd.db.LOCAL_SUBS_ID

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
    private val batchMutex = MutexState()
    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }

    val settingsDialogVisibleFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val powerWarningItemFlow: StateFlow<SubsItem?>
        field = MutableStateFlow(null)

    val uiState: StateFlow<Loadable<SubsManageUiState>> =
        SubscriptionRepository.snapshotFlow.flatMapLatest { snapshotState ->
            when (snapshotState) {
                Loadable.Loading -> flowOf(Loadable.Loading)
                is Loadable.Failure -> flowOf(snapshotState)
                is Loadable.Ready -> combine(
                    Db.subsItemDao.query(),
                    SubscriptionRepository.updating,
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
        AppStore.updateSettings { it.copy(updateSubsInterval = value) }
    }

    fun setPowerWarningEnabled(enabled: Boolean) {
        AppStore.updateSettings { it.copy(subsPowerWarn = enabled) }
    }

    fun setSettingsDialogVisible(visible: Boolean) {
        settingsDialogVisibleFlow.value = visible
    }

    fun toggleMatching() {
        AppStore.updateSettings { it.copy(enableMatch = !it.enableMatch) }
    }

    fun enableMatching() {
        AppStore.updateSettings { it.copy(enableMatch = true) }
    }

    fun refresh() {
        scope.launchUi {
            SubscriptionRepository.refresh().message?.let { toast(it) }
        }
    }

    suspend fun deleteSubscriptions(ids: Set<Long>): SubscriptionResult =
        SubscriptionRepository.delete(*(ids - LOCAL_SUBS_ID).toLongArray())

    fun updateOrder(items: List<SubsItem>) {
        scope.launchUi {
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
        scope.launchUi {
            Db.subsItemDao.updateEnable(item.id, enabled)
        }
    }

    suspend fun addOrModifySubscription(
        url: String,
        oldItem: SubsItem? = null,
    ): SubscriptionResult = SubscriptionRepository.addOrModifyRemote(url, oldItem)
}
