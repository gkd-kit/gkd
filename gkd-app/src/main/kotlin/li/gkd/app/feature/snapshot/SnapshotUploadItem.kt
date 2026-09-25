package li.gkd.app.feature.snapshot

import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.data.snapshot.SnapshotUploadArchive
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GithubUploadItem
import li.gkd.app.util.IMPORT_SHORT_URL
import li.gkd.db.Snapshot
import java.io.IOException

fun createSnapshotUploadItem(
    snapshot: Snapshot,
    label: String,
    vm: SnapshotVm,
): GithubUploadItem {
    var prepared: SnapshotUploadArchive? = null
    return GithubUploadItem(
        label = label,
        getFile = {
            vm.buildUploadArchive(snapshot).also { prepared = it }.file
        },
        onSuccessResult = { asset ->
            val archive = checkNotNull(prepared)
            if (!vm.markUploaded(snapshot, asset.id, archive.screenshotModifiedAt)) {
                throw IOException(UiStrings.snapshot_upload_changed)
            }
        },
        releaseFile = { file ->
            SnapshotRepository.deleteArchive(file)
            prepared = null
        },
        showHref = { IMPORT_SHORT_URL + it.id },
    )
}
