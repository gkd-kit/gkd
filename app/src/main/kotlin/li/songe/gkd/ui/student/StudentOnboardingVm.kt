package li.songe.gkd.ui.student

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.MainActivity
import li.songe.gkd.MainViewModel
import li.songe.gkd.db.DbSet
import li.songe.gkd.permission.appOpsRestrictStateList
import li.songe.gkd.permission.appOpsRestrictedFlow as permissionAppOpsRestrictedFlow
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.service.StatusService
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
    val appOpsRestrictedFlow = permissionAppOpsRestrictedFlow

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                appOpsRestrictStateList.forEach { it.updateAndGet() }
                delay(1000)
            }
        }
    }

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

    val recommendedCandidatesFlow = combine(
        recommendedSubscriptionFlow,
        appInfoMapFlow,
    ) { subscription, installedApps ->
        buildStudentCandidates(
            subscription = subscription,
            installedApps = installedApps,
            selectedAppIds = emptySet(),
            query = "",
        )
    }.stateInit(emptyList())

    val defaultSelectedAppIdsFlow = recommendedCandidatesFlow.mapNew { candidates ->
        buildDefaultStudentSelectedAppIds(candidates)
    }

    val supportedInstalledCountFlow = recommendedCandidatesFlow.mapNew { candidates ->
        candidates.size
    }

    val defaultSelectedCountFlow = defaultSelectedAppIdsFlow.mapNew { selectedAppIds ->
        selectedAppIds.size
    }

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

    val canQuickApplyFlow = combine(
        canQueryPackagesFlow,
        isImportingSubscriptionFlow,
        isApplyingFlow,
    ) { canQueryPackages, isImporting, isApplying ->
        canQueryPackages && !isImporting && !isApplying
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

    fun seedDefaultSelection() {
        val defaultSelectedAppIds = defaultSelectedAppIdsFlow.value
        if (defaultSelectedAppIds.isNotEmpty()) {
            selectedAppIdsMutableFlow.value = defaultSelectedAppIds
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

    fun prepareStudentSelection(mainVm: MainViewModel) = viewModelScope.launchTry(Dispatchers.IO) {
        if (isImportingSubscriptionFlow.value || isApplyingFlow.value) return@launchTry
        if (!canQueryPackagesFlow.value) return@launchTry
        if (!ensureRecommendedSubscription(mainVm)) {
            toast("推荐订阅导入失败，请稍后重试")
            return@launchTry
        }
        if (selectedAppIdsMutableFlow.value.isEmpty()) {
            val defaultSelectedAppIds = buildDefaultStudentSelectedAppIds(
                buildStudentCandidates(
                    subscription = recommendedSubscriptionFlow.value,
                    installedApps = appInfoMapFlow.value,
                    selectedAppIds = emptySet(),
                    query = "",
                )
            )
            selectedAppIdsMutableFlow.value = defaultSelectedAppIds
            if (defaultSelectedAppIds.isEmpty()) {
                toast("没有识别到学校常用 App，请手动选择")
            }
        }
    }

    fun applySelectedPlan(context: MainActivity) = viewModelScope.launchTry {
        if (isApplyingFlow.value) return@launchTry
        isApplyingFlow.value = true
        try {
            val applied = withContext(Dispatchers.IO) {
                applySelectedPlanInternal(selectedAppIdsMutableFlow.value)
            }
            if (applied) {
                StatusService.requestStart(context)
            }
        } finally {
            isApplyingFlow.value = false
        }
    }

    private suspend fun ensureRecommendedSubscription(mainVm: MainViewModel): Boolean {
        if (recommendedSubscriptionReadyFlow.value) return true

        isImportingSubscriptionFlow.value = true
        try {
            mainVm.addOrModifySubs(
                url = STUDENT_RECOMMENDED_SUBSCRIPTION_URL,
                oldItem = recommendedSubsItemFlow.value,
            ).join()
            if (recommendedSubscriptionReadyFlow.value) return true
            return withTimeoutOrNull(5_000) {
                recommendedSubscriptionReadyFlow.filter { ready -> ready }.first()
            } == true
        } finally {
            isImportingSubscriptionFlow.value = false
        }
    }

    private suspend fun applySelectedPlanInternal(
        requestedSelectedAppIds: Set<String>,
    ): Boolean {
        val subsItem = recommendedSubsItemFlow.value
        val subscription = recommendedSubscriptionFlow.value
        if (subsItem == null || subscription == null) {
            toast("请先导入推荐订阅")
            return false
        }

        val installedAppIds = appInfoMapFlow.value.keys
        val supportedAppIds = subscription.apps.asSequence()
            .filter { app -> app.groups.isNotEmpty() }
            .map { app -> app.id }
            .toHashSet()
        val selectedAppIds = requestedSelectedAppIds
            .filterTo(hashSetOf()) { appId ->
                supportedAppIds.contains(appId) && installedAppIds.contains(appId)
            }

        if (selectedAppIds.isEmpty()) {
            toast("请先选择至少一个 App")
            return false
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
            buildStudentCompletedStore(store)
        }
        toast("已为 ${selectedAppIds.size} 个 App 开启学生入门配置")
        return true
    }
}
