package li.gkd.app.ui.snapshot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import li.gkd.app.platform.PlatformResult
import li.gkd.app.resources.Res
import li.gkd.app.resources.delete_success
import li.gkd.app.resources.platform_action_unsupported
import li.gkd.app.resources.save_success
import li.gkd.app.resources.saving_progress
import li.gkd.app.resources.screenshot_replace_success
import li.gkd.app.resources.screenshot_size_mismatch
import li.gkd.app.resources.snapshot_delete
import li.gkd.app.resources.snapshot_delete_confirmation
import li.gkd.app.resources.snapshot_generate_link
import li.gkd.app.resources.snapshot_upload_privacy_warning
import li.gkd.app.resources.upload_busy
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.snapshot.platform.saveImageToAlbum
import li.gkd.app.storage.FileExports
import li.gkd.app.storage.readFileBytes
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.util.LogUtils
import li.gkd.db.Snapshot
import org.jetbrains.compose.resources.getString

/** Both hosts use the same confirmation and feedback; only native file actions differ. */
class SnapshotActionCoordinator(
    private val platform: SnapshotPlatformActions,
    private val scope: CoroutineScope,
    private val dialogs: DialogRequests,
    private val toast: (String) -> Unit,
    private val startUpload: (Snapshot) -> Boolean,
    private val fileScope: CoroutineScope,
    private val onReplaced: (Long) -> Unit,
    private val onBeforeDelete: () -> Unit,
    private val onDeleteFailed: () -> Unit,
) : SnapshotActions {

    override fun share(snapshot: Snapshot) {
        launchUi(fileScope, toast) {
            // A share target may read the archive after this call returns; cache expiry owns cleanup.
            val archive =
                SnapshotStore.createArchive(snapshot.id, snapshot.appId, snapshot.activityId)
            if (platform.share(archive) == PlatformResult.Unsupported) {
                SnapshotStore.deleteArchive(archive)
                toast(getString(Res.string.platform_action_unsupported))
            }
        }
    }

    override fun replace(snapshot: Snapshot) {
        launchUi(fileScope, toast) {
            val source = platform.pickImage() ?: return@launchUi
            if (SnapshotStore.replaceScreenshot(snapshot, readFileBytes(source))) {
                onReplaced(snapshot.id)
                toast(getString(Res.string.screenshot_replace_success))
            } else toast(getString(Res.string.screenshot_size_mismatch))
        }
    }

    override fun saveToDownloads(snapshot: Snapshot) = saveOne(snapshot, false)
    override fun saveToAlbum(snapshot: Snapshot) = saveOne(snapshot, true)

    private fun saveOne(snapshot: Snapshot, album: Boolean) {
        launchUi(fileScope, toast) {
            if (!platform.ensureSavePermission()) return@launchUi
            toast(getString(Res.string.saving_progress))
            when (val result = save(snapshot, album)) {
                is PlatformResult.Success -> if (result.value) toast(getString(Res.string.save_success))
                PlatformResult.Unsupported -> toast(getString(Res.string.platform_action_unsupported))
            }
        }
    }

    override suspend fun saveSelectedToDownloads(snapshots: List<Snapshot>) =
        saveSelected(snapshots, false)

    override suspend fun saveSelectedToAlbum(snapshots: List<Snapshot>) =
        saveSelected(snapshots, true)

    private suspend fun saveSelected(snapshots: List<Snapshot>, album: Boolean): Int? {
        if (!platform.ensureSavePermission()) return null
        toast(getString(Res.string.saving_progress))
        var saved = 0
        for (snapshot in snapshots) {
            try {
                when (val result = save(snapshot, album)) {
                    is PlatformResult.Success -> if (result.value) saved++ else break
                    PlatformResult.Unsupported -> {
                        toast(getString(Res.string.platform_action_unsupported))
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
                    toast(getString(Res.string.upload_busy))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.displayMessage())
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
                    toast(getString(Res.string.delete_success))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.displayMessage())
            }
        }
    }
}
