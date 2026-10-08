package li.gkd.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.crash.CrashData
import li.gkd.app.permission.PermissionRequester
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.ShareLogState
import li.gkd.app.ui.component.TextDialogState
import li.gkd.app.ui.home.HomeNavigation
import li.gkd.app.ui.navigation.AppNavigator
import li.gkd.app.ui.navigation.CrashReportRoute
import li.gkd.app.ui.settings.appVersion
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.share.confirmDelete as confirmRuleDeletion
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.subscription.RuleControlDialogState
import li.gkd.app.ui.subscription.RuleGroupState
import li.gkd.app.ui.subscription.SubsLinkDialogState
import li.gkd.app.ui.subscription.SubsSheetState
import li.gkd.app.ui.update.UpdateStatus
import li.gkd.app.ui.upload.GithubUploadState

class MainViewModel(val permissions: PermissionRequester) : BaseViewModel() {
    companion object {
        private var current: MainViewModel? = null

        fun requireCurrent(): MainViewModel =
            checkNotNull(current) { "Main UI is not initialized" }
    }

    fun registerCurrent() {
        check(current == null || current === this) { "Main UI is already registered" }
        current = this
    }

    init {
        addCloseable {
            if (current === this) current = null
        }
    }

    val navigator = AppNavigator(scope)

    val dialogRequests = DialogRequests()

    fun confirmDelete(
        title: String,
        text: String,
        targets: () -> Set<DeletionTarget> = { emptySet() },
        dismiss: () -> Unit = {},
        delete: suspend () -> Unit,
    ) = scope.launchUi {
        confirmRuleDeletion(
            dialogRequests, navigator, subsSheet, ruleGroupState, ruleControlDialog,
            title, text, targets, dismiss, delete,
        )
    }

    val updateStatus = appVersion().let { version ->
        if (version.updateEnabled) UpdateStatus(
            scope,
            version.versionCode.toIntOrNull() ?: 0,
            version.versionName,
        ) else null
    }

    val githubUpload = GithubUploadState(
        scope = scope,
        navigator = navigator,
    )

    val shareLog = ShareLogState(
        scope = scope,
        githubUpload = githubUpload,
    )

    val subsLinkDialog = SubsLinkDialogState(
        navigator = navigator,
        permissions = permissions,
    )

    val textDialog = TextDialogState()

    val subsSheet = SubsSheetState(
        navigator = navigator,
        openUrl = textDialog::showUrl,
        requestUrl = subsLinkDialog::request,
        confirmDelete = ::confirmDelete,
    )

    val ruleGroupState = RuleGroupState()
    fun showRuleGroup(
        subscriptionId: Long,
        appId: String?,
        group: RawSubscription.RawGroupProps,
        pageAppId: String?,
    ) = scope.launch {
        withContext(Dispatchers.Default) { group.cacheStr }
        ruleGroupState.showGroup(
            if (group is RawSubscription.RawGlobalGroup) {
                RuleGroupTarget.Global(subscriptionId, group.key, pageAppId)
            } else {
                RuleGroupTarget.App(subscriptionId, requireNotNull(appId), group.key)
            },
        )
    }

    val ruleControlDialog = RuleControlDialogState()

    val homeNavigation = HomeNavigation()

    private var pendingCrashes = emptyList<CrashData>()

    fun showCrashReports(records: List<CrashData>) {
        pendingCrashes = records
        if (records.isNotEmpty()) navigator.navigate(CrashReportRoute)
    }

    fun takeCrashDataList(): List<CrashData> = pendingCrashes.also { pendingCrashes = emptyList() }
}
