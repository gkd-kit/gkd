package li.gkd.app.ui.upload

import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.snapshot_upload_changed
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.snapshot.SnapshotUploadArchive
import li.gkd.db.Snapshot
import org.jetbrains.compose.resources.getString
import java.io.IOException

fun createSnapshotUploadItem(
    snapshot: Snapshot,
    label: String
): GithubUploadItem {
    var prepared: SnapshotUploadArchive? = null
    return GithubUploadItem(
        label,
        getFile = { SnapshotStore.createUploadArchive(snapshot.id).also { prepared = it }.file },
        onSuccessResult = { asset ->
            if (!SnapshotStore.markUploaded(
                    snapshot.id,
                    asset.id,
                    checkNotNull(prepared).screenshotModifiedAt
                )
            ) {
                throw IOException(getString(Res.string.snapshot_upload_changed))
            }
        },
        releaseFile = { SnapshotStore.deleteArchive(it); prepared = null },
        showHref = { AppLinks.ImportSnapshot + it.id },
    )
}
