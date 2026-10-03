package li.gkd.app.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.state.MutexState
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.subscription.SubscriptionResult
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.subscription.SubsManageUiState
import li.gkd.db.Db
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.db.SubsItem

/** Business state owned by the HomeRoute navigation entry. */
class HomeViewModel : BaseViewModel() {
    val homeState = HomeState()
    val latestState = HomeDataSources.observeLatest().stateInit(Loadable.Loading)

    val appsState = AppListSources.observe(
        AppInfoRepository.state,
        ruleGroupState,
        Db.actionLogDao.queryLatestUniqueAppIds(),
        Db.appLastVisitDao.query(),
        globalGroupCounts = { it.groups.appIdToGlobalGroupCount },
        appGroups = { it.groups.appIdToAllGroups },
    ).catch { emit(Loadable.Failure(it)) }.stateInit(Loadable.Loading)

    private val batchMutex = MutexState()
    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }

    val powerWarningItemFlow: StateFlow<SubsItem?>
        field = MutableStateFlow(null)

    val subscriptionsState: StateFlow<Loadable<SubsManageUiState>> =
        SubscriptionRepository.snapshotFlow.flatMapLatest { snapshotState ->
            when (snapshotState) {
                Loadable.Loading -> flowOf(Loadable.Loading)
                is Loadable.Failure -> flowOf(snapshotState)
                is Loadable.Ready -> Db.subsItemDao.query().map { subItems ->
                    SubsManageUiState(
                        subItems = subItems,
                        subscriptions = snapshotState.value.subscriptions,
                        loadErrors = snapshotState.value.loadErrors,
                        refreshErrors = snapshotState.value.updateErrors,
                    )
                }.map<SubsManageUiState, Loadable<SubsManageUiState>> { Loadable.Ready(it) }
                    .catch { emit(Loadable.Failure(it)) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)

    fun setUpdateInterval(value: Long) {
        SettingsRepository.updateSettings { it.copy(updateSubsInterval = value) }
    }

    fun setPowerWarningEnabled(enabled: Boolean) {
        SettingsRepository.updateSettings { it.copy(subsPowerWarn = enabled) }
    }


    fun toggleMatching() {
        SettingsRepository.updateSettings { it.copy(enableMatch = !it.enableMatch) }
    }

    fun enableMatching() {
        SettingsRepository.updateSettings { it.copy(enableMatch = true) }
    }

    suspend fun refreshSubscriptions() = SubscriptionRepository.refresh()

    suspend fun deleteSubscriptions(ids: Set<Long>): SubscriptionResult =
        SubscriptionRepository.delete(*(ids - LOCAL_SUBS_ID).toLongArray())

    suspend fun updateOrder(items: List<SubsItem>) = Db.subsItemDao.batchUpdateOrder(items)

    suspend fun requestSubscriptionEnabled(item: SubsItem, enabled: Boolean) {
        if (!SubscriptionRepository.requestEnabled(item.id, enabled)) {
            powerWarningItemFlow.value = item
        }
    }

    fun dismissPowerWarning() {
        powerWarningItemFlow.value = null
    }

    suspend fun confirmPowerWarning() {
        val item = powerWarningItemFlow.value ?: return
        powerWarningItemFlow.value = null
        SubscriptionRepository.requestEnabled(item.id, true, confirmed = true)
    }

    suspend fun addOrModifySubscription(
        url: String,
        oldItem: SubsItem? = null,
    ): SubscriptionResult = SubscriptionRepository.addOrModifyRemote(url, oldItem)
}
