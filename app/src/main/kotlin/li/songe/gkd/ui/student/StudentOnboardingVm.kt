package li.songe.gkd.ui.student

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainViewModel
import li.songe.gkd.db.DbSet
import li.songe.gkd.permission.appOpsRestrictedFlow as permissionAppOpsRestrictedFlow
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.subsMapFlow
import li.songe.gkd.util.toast

class StudentOnboardingVm : BaseViewModel() {

    val searchStrFlow = MutableStateFlow("")
    private val debounceSearchStrFlow = searchStrFlow.debounce(200).stateInit(searchStrFlow.value)

    private val selectedAppIdsMutableFlow = MutableStateFlow<Set<String>>(emptySet())
    val selectedAppIdsFlow = selectedAppIdsMutableFlow

    val isImportingSubscriptionFlow = MutableStateFlow(false)
    val isApplyingFlow = MutableStateFlow(false)

    val canQueryPackagesFlow = canQueryPkgState.stateFlow
    val notificationGrantedFlow = notificationState.stateFlow
    val writeSecureSettingsGrantedFlow = writeSecureSettingsState.stateFlow
    val appOpsRestrictedFlow = permissionAppOpsRestrictedFlow

    val recommendedSubsItemFlow = subsItemsFlow.mapNew { subsItems ->
        subsItems.find { item -> item.id == STUDENT_RECOMMENDED_SUBSCRIPTION_ID }
    }

    val recommendedSubscriptionFlow = subsMapFlow.mapNew { subsMap ->
        subsMap[STUDENT_RECOMMENDED_SUBSCRIPTION_ID]
    }

    val recommendedSubscriptionReadyFlow = combine(
        recommendedSubsItemFlow,
        recommendedSubscriptionFlow,
    ) { subsItem, subscription ->
        subsItem != null && subscription != null
    }.stateInit(false)

    val candidatesFlow = combine(
        recommendedSubscriptionFlow,
        appInfoMapFlow,
        selectedAppIdsFlow,
        debounceSearchStrFlow,
    ) { subscription, installedApps, selectedAppIds, query ->
        buildStudentCandidates(
            subscription = subscription,
            installedApps = installedApps,
            selectedAppIds = selectedAppIds,
            query = query,
        )
    }.stateInit(emptyList())

    val selectedCountFlow = selectedAppIdsFlow.mapNew { it.size }

    val canApplyFlow = combine(
        recommendedSubsItemFlow,
        recommendedSubscriptionFlow,
        selectedAppIdsFlow,
        isApplyingFlow,
    ) { subsItem, subscription, selectedAppIds, isApplying ->
        subsItem != null && subscription != null && selectedAppIds.isNotEmpty() && !isApplying
    }.stateInit(false)

    val completedFlow = storeFlow.mapNew { store ->
        store.studentOnboardingCompletedVersion >= STUDENT_ONBOARDING_VERSION
    }

    fun addRecommendedSubscription(mainVm: MainViewModel) {
        if (isImportingSubscriptionFlow.value) return
        isImportingSubscriptionFlow.value = true
        val oldItem = recommendedSubsItemFlow.value
        mainVm.addOrModifySubs(
            url = STUDENT_RECOMMENDED_SUBSCRIPTION_URL,
            oldItem = oldItem,
        ).invokeOnCompletion {
            isImportingSubscriptionFlow.value = false
        }
    }

    fun setAppSelected(appId: String, selected: Boolean) {
        selectedAppIdsMutableFlow.update { selectedAppIds ->
            if (selected) {
                selectedAppIds + appId
            } else {
                selectedAppIds - appId
            }
        }
    }

    fun clearSelectedApps() {
        selectedAppIdsMutableFlow.value = emptySet()
    }

    fun applySelectedPlan() = viewModelScope.launchTry(Dispatchers.IO) {
        if (isApplyingFlow.value) return@launchTry
        isApplyingFlow.value = true
        try {
            val subsItem = recommendedSubsItemFlow.value
            val subscription = recommendedSubscriptionFlow.value
            if (subsItem == null || subscription == null) {
                toast("请先导入推荐订阅")
                return@launchTry
            }

            val installedAppIds = appInfoMapFlow.value.keys
            val supportedAppIds = subscription.apps.asSequence()
                .filter { app -> app.groups.isNotEmpty() }
                .map { app -> app.id }
                .toHashSet()
            val selectedAppIds = selectedAppIdsMutableFlow.value
                .filterTo(hashSetOf()) { appId ->
                    supportedAppIds.contains(appId) && installedAppIds.contains(appId)
                }

            if (selectedAppIds.isEmpty()) {
                toast("请先选择至少一个 App")
                return@launchTry
            }

            val appConfigs = buildStudentAppConfigs(
                subsId = subsItem.id,
                subscription = subscription,
                selectedAppIds = selectedAppIds,
                existingConfigs = DbSet.appConfigDao.queryAll(),
            )
            val globalDisableConfigs = buildStudentGlobalDisableConfigs(
                subsId = subsItem.id,
                subscription = subscription,
                existingConfigs = DbSet.subsConfigDao.queryAll(),
            )

            if (appConfigs.isNotEmpty()) {
                DbSet.appConfigDao.insert(*appConfigs.toTypedArray())
            }
            if (globalDisableConfigs.isNotEmpty()) {
                DbSet.subsConfigDao.insert(*globalDisableConfigs.toTypedArray())
            }
            DbSet.subsItemDao.updateEnable(subsItem.id, true)
            storeFlow.update { store ->
                store.copy(
                    studentOnboardingCompletedVersion = STUDENT_ONBOARDING_VERSION,
                    studentOnboardingCardDismissedVersion = STUDENT_ONBOARDING_VERSION,
                )
            }
            toast("已为 ${selectedAppIds.size} 个 App 开启学生入门配置")
        } finally {
            isApplyingFlow.value = false
        }
    }
}
