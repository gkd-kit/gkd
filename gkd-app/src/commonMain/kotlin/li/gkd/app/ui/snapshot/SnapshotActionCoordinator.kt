package li.gkd.app.ui.snapshot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.snapshot.platform.saveImageToAlbum
import li.gkd.app.storage.FileExports
import li.gkd.app.storage.readFileBytes
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.ui.upload.createSnapshotUploadItem
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils
import li.gkd.db.Snapshot
import org.jetbrains.compose.resources.getString

/** Both hosts use the same confirmation and feedback; only native file actions differ. */
class SnapshotActionCoordinator(
    private val platform: SnapshotPlatformActions,
    private val scope: CoroutineScope,
    private val dialogs: DialogRequests,
    private val startUpload: (Snapshot) -> Boolean,
    private val fileScope: CoroutineScope,
    private val onReplaced: (Long) -> Unit,
    private val onBeforeDelete: () -> Unit,
    private val onDeleteFailed: () -> Unit,
) : SnapshotActions {

    override fun replace(snapshot: Snapshot) {
        fileScope.launchUi {
            val source = platform.pickImage() ?: return@launchUi
            if (SnapshotStore.replaceScreenshot(snapshot, readFileBytes(source))) {
                onReplaced(snapshot.id)
                ToastUtils.show(getString(Res.string.screenshot_replace_success))
            } else ToastUtils.show(getString(Res.string.screenshot_size_mismatch))
        }
    }

    override fun saveToDownloads(snapshot: Snapshot) = saveOne(snapshot, false)
    override fun saveToAlbum(snapshot: Snapshot) = saveOne(snapshot, true)

    private fun saveOne(snapshot: Snapshot, album: Boolean) {
        fileScope.launchUi {
            if (!platform.ensureSavePermission()) return@launchUi
            ToastUtils.show(getString(Res.string.saving_progress))
            when (val result = save(snapshot, album)) {
                is PlatformResult.Success -> if (result.value) ToastUtils.show(getString(Res.string.save_success))
                PlatformResult.Unsupported -> ToastUtils.show(getString(Res.string.platform_action_unsupported))
            }
        }
    }

    override suspend fun saveSelectedToDownloads(snapshots: List<Snapshot>) =
        saveSelected(snapshots, false)

    override suspend fun saveSelectedToAlbum(snapshots: List<Snapshot>) =
        saveSelected(snapshots, true)

    private suspend fun saveSelected(snapshots: List<Snapshot>, album: Boolean): Int? {
        if (!platform.ensureSavePermission()) return null
        ToastUtils.show(getString(Res.string.saving_progress))
        var saved = 0
        for (snapshot in snapshots) {
            try {
                when (val result = save(snapshot, album)) {
                    is PlatformResult.Success -> if (result.value) saved++ else break
                    PlatformResult.Unsupported -> {
                        ToastUtils.show(getString(Res.string.platform_action_unsupported))
                        return null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d(e)
            }
        }
        return saved
    }

    private suspend fun save(snapshot: Snapshot, album: Boolean): PlatformResult<Boolean> =
        if (album) {
            when (saveImageToAlbum(SnapshotStore.screenshotFile(snapshot.id))) {
                is PlatformResult.Success -> PlatformResult.Success(true)
                PlatformResult.Unsupported -> PlatformResult.Unsupported
            }
        } else {
            FileExports.withTemporaryFile(
                create = {
                    SnapshotStore.createArchive(
                        snapshot.id,
                        snapshot.appId,
                        snapshot.activityId
                    )
                },
                delete = SnapshotStore::deleteArchive,
                consume = platform::save,
            )
        }

    override fun upload(snapshot: Snapshot) {
        scope.launch {
            try {
                if (dialogs.confirm(
                        title = getString(Res.string.snapshot_generate_link),
                        text = getString(Res.string.snapshot_upload_privacy_warning),
                        confirmText = getString(Res.string.snapshot_generate_link),
                    ) && !startUpload(snapshot)
                ) {
                    ToastUtils.show(getString(Res.string.upload_busy))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastUtils.show(e.displayMessage())
            }
        }
    }

    override fun delete(snapshot: Snapshot) {
        scope.launch {
            try {
                if (dialogs.confirm(
                        title = getString(Res.string.snapshot_delete),
                        text = getString(Res.string.snapshot_delete_confirmation),
                        error = true,
                    )
                ) {
                    onBeforeDelete()
                    try {
                        SnapshotStore.delete(snapshot)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onDeleteFailed(); throw e
                    }
                    ToastUtils.show(getString(Res.string.delete_success))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastUtils.show(e.displayMessage())
            }
        }
    }
}

fun UiHost.snapshotActions(
    mainVm: MainViewModel,
    vm: SnapshotViewModel,
    onReplaced: (Long) -> Unit,
    beforeDelete: () -> Unit,
    deleteFailed: () -> Unit,
): SnapshotActions {
    return SnapshotActionCoordinator(
        platform = snapshotPlatformActions(),
        scope = mainVm.scope,
        dialogs = mainVm.dialogRequests,
        startUpload = { mainVm.githubUpload.startTask(createSnapshotUploadItem(it, it.appId)) },
        fileScope = vm.scope,
        onReplaced = onReplaced,
        onBeforeDelete = beforeDelete,
        onDeleteFailed = deleteFailed,
    )
}
