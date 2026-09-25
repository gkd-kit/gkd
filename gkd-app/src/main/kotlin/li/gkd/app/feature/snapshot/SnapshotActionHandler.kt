package li.gkd.app.feature.snapshot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.MainActivity
import li.gkd.app.MainViewModel
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.permission.PermissionStates
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.LogUtils
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.SystemDownloads
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.UriUtils
import li.gkd.db.Snapshot

class SnapshotActionHandler(
    private val mainVm: MainViewModel,
    private val vm: SnapshotVm,
    private val activity: MainActivity,
    private val onReplaced: (Long) -> Unit = {},
    private val onBeforeDelete: () -> Unit = {},
    private val onDeleteFailed: () -> Unit = {},
) {
    fun share(snapshot: Snapshot) {
        vm.scope.launchUi {
            activity.shareFile(vm.buildShareArchive(snapshot), UiStrings.snapshot_share)
        }
    }

    fun saveToDownloads(snapshot: Snapshot) {
        vm.scope.launchUi {
            toast(UiStrings.saving_progress)
            FolderUtils.withTemporaryZip(
                create = { vm.buildShareArchive(snapshot) },
                delete = SnapshotRepository::deleteArchive,
            ) { archive ->
                activity.saveFileToDownloads(archive)
            }
        }
    }

    suspend fun saveSelectedToDownloads(snapshots: List<Snapshot>): Int? {
        if (!mainVm.permissionRequests.ensurePermissions(PermissionStates.writeExternalStorage)) return null
        toast(UiStrings.saving_progress)
        var savedCount = 0
        snapshots.forEach { snapshot ->
            try {
                val saved = FolderUtils.withTemporaryZip(
                    create = { vm.buildShareArchive(snapshot) },
                    delete = SnapshotRepository::deleteArchive,
                ) { archive ->
                    SystemDownloads.save(archive) != null
                }
                if (saved) savedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d(e)
            }
        }
        return savedCount
    }

    fun upload(snapshot: Snapshot) {
        vm.scope.launchUi {
            if (!mainVm.dialogRequests.confirm(
                    title = UiStrings.snapshot_generate_link,
                    text = UiStrings.snapshot_upload_privacy_warning,
                    confirmText = UiStrings.snapshot_generate_link,
                )) return@launchUi
            val uploadItem = createSnapshotUploadItem(snapshot, snapshot.appId, vm)
            if (!mainVm.githubUpload.startTask(uploadItem)) toast(UiStrings.upload_busy)
        }
    }

    fun saveToAlbum(snapshot: Snapshot) {
        vm.scope.launchUi {
            toast(UiStrings.saving_progress)
            if (!mainVm.permissionRequests.ensurePermissions(
                    PermissionStates.writeExternalStorage,
                )) return@launchUi
            vm.saveScreenshotToAlbum(snapshot)
            toast(UiStrings.save_success)
        }
    }

    suspend fun saveSelectedToAlbum(snapshots: List<Snapshot>): Int? {
        if (!mainVm.permissionRequests.ensurePermissions(PermissionStates.writeExternalStorage)) return null
        toast(UiStrings.saving_progress)
        var savedCount = 0
        snapshots.forEach { snapshot ->
            try {
                if (vm.saveScreenshotToAlbum(snapshot)) savedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d(e)
            }
        }
        return savedCount
    }

    fun replace(snapshot: Snapshot) {
        vm.scope.launchUi {
            val uri = mainVm.activityResults.pickImage() ?: return@launchUi
            val newBytes = withContext(Dispatchers.IO) { UriUtils.uri2Bytes(uri) }
            if (vm.replaceScreenshot(snapshot, newBytes)) {
                onReplaced(snapshot.id)
                toast(UiStrings.screenshot_replace_success)
            } else {
                toast(UiStrings.screenshot_size_mismatch)
            }
        }
    }

    fun delete(snapshot: Snapshot) {
        mainVm.confirmDelete(
            title = UiStrings.snapshot_delete,
            text = UiStrings.snapshot_delete_confirmation,
        ) {
            onBeforeDelete()
            try {
                vm.deleteSnapshot(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDeleteFailed()
                throw e
            }
            toast(UiStrings.delete_success)
        }
    }
}
