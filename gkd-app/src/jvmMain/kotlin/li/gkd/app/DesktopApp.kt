package li.gkd.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import li.gkd.app.backup.BackupManager
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.backup_import_progress
import li.gkd.app.storage.FileExports
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.appStorage
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.TextDialogState
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.settings.SettingsFeedback
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.subscription.RuleControlDialogState
import li.gkd.app.ui.subscription.RuleGroupState
import li.gkd.app.ui.subscription.SubsLinkDialogState
import li.gkd.app.ui.subscription.SubsSheetHost
import li.gkd.app.ui.subscription.SubsSheetState
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.ui.text.getSync
import java.awt.Window
import java.io.File

class DesktopSession(
    val state: DesktopState,
    val runtime: DesktopRuntime,
    private val tasks: DesktopTasks,
    fileDialogOwner: () -> Window
) {
    val fileActions = DesktopFileActions(fileDialogOwner)
    val revision = state.revision
    var showShareLogs by mutableStateOf(false)
    var backupPath by mutableStateOf("")
    fun exportBackup(save: Boolean = false, share: Boolean = false) {
        scope.launch {
            try {
                if (share) {
                    state.unsupported(); return@launch
                }
                runtime.subscriptionInitialization.join()
                val file = BackupManager.exportData()
                backupPath = if (save) "" else file.absolutePath
                state.record("backup-export:" + file.absolutePath)
                when {
                    save -> FileExports.withTemporaryFile(
                        create = { file },
                        delete = { it.delete() },
                        consume = fileActions::saveAs
                    )

                    else -> state.toast.show(
                        file.absolutePath
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.toast.show(e.displayMessage())
            }
        }
    }

    fun importBackup(selectFile: Boolean = true) {
        scope.launch {
            try {
                val file = if (selectFile) fileActions.choose(appStorage().sharedCache)
                    ?: return@launch else File(backupPath)
                runtime.subscriptionInitialization.join()
                state.toast.show(Res.string.backup_import_progress.getSync())
                val skipped = BackupManager.importData(FileSource.Local(file))
                state.record("backup-import:" + file.absolutePath)
                state.toast.show(
                    SettingsFeedback.backupImported(
                        skipped
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.toast.show(e.displayMessage())
            }
        }
    }

    fun exportLogs(save: Boolean = false, share: Boolean = false) {
        scope.launch {
            try {
                if (share) {
                    state.unsupported(); return@launch
                }
                val file = runtime.exportLogs()
                state.record("logs-export:" + file.absolutePath)
                when {
                    save -> FileExports.withTemporaryFile(
                        create = { file },
                        delete = { it.delete() },
                        consume = fileActions::saveAs
                    )

                    else -> state.toast.show(
                        file.absolutePath
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.toast.show(e.displayMessage())
            }
        }
    }

    val dialogs = DialogRequests()
    val textDialog =
        TextDialogState { state.navigate(WebViewRoute(it)) }
    val links = SubsLinkDialogState(
        state.toast::show,
        onOpenHelp = { state.navigate(WebViewRoute(AppLinks.SubscriptionHelp)) },
        requestLocalNetworkPermission = { state.environment.android.localNetworkGranted.also { if (!it) state.unsupported() } })
    val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val githubUpload get() = tasks.githubUpload
    val updateStatus get() = tasks.updateStatus
    val rules = RuleGroupState()
    val ruleControl =
        RuleControlDialogState()
    val subsSheet = SubsSheetState() {
        SubsSheetHost(
            DesktopProfile.appName,
            { state.navigate(it) },
            { state.navigate(WebViewRoute(it)) },
            { links.request(it) },
            state.toast::show,
            { title, text, targets, dismiss, delete ->
                scope.launch {
                    if (dialogs.confirm(title, text, error = true)) {
                        val deleted = targets()
                        dismiss()
                        subsSheetDismiss(deleted)
                        val first =
                            state.backStack.indexOfFirst { route -> deleted.any { it.owns(route) } }
                        if (first > 0) while (state.backStack.size > first) state.backStack.removeLast()
                        try {
                            delete()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            state.toast.show(e.displayMessage())
                        }
                    }
                }
            },
        )
    }

    private fun subsSheetDismiss(targets: Set<DeletionTarget>) {
        subsSheet.dismissForDeletion(targets); rules.dismissForDeletion(targets); ruleControl.dismissForDeletion(
            targets
        )
    }

    fun confirmDelete(
        title: String,
        text: String,
        targets: () -> Set<DeletionTarget>,
        dismiss: () -> Unit,
        delete: suspend () -> Unit
    ) {
        scope.launch {
            if (dialogs.confirm(title, text, error = true)) {
                val deleted = targets(); dismiss(); subsSheetDismiss(deleted)
                val first = state.backStack.indexOfFirst { route -> deleted.any { it.owns(route) } }
                if (first > 0) while (state.backStack.size > first) state.backStack.removeLast()
                try {
                    delete()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.toast.show(e.displayMessage())
                }
            }
        }
    }

}
