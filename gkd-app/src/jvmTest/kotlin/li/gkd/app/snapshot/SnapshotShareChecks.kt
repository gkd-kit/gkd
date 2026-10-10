package li.gkd.app.snapshot

import androidx.lifecycle.ViewModelStore
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import li.gkd.app.platform.PlatformResult
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.ui.snapshot.SnapshotPlatformActions
import li.gkd.app.ui.snapshot.SnapshotViewModel
import li.gkd.db.Db
import li.gkd.db.Snapshot

/** Uses the integration test's real application lifetime and isolated snapshot storage. */
suspend fun assertSnapshotSharing(layout: AppStorageLayout) = withContext(Dispatchers.Main) {
    val targets = (2L..4L).map { Snapshot(it, "share.test", null, 1, 1, false) }
    targets.forEach { snapshot ->
        Db.snapshotDao.insert(snapshot)
        layout.snapshot.resolve("${snapshot.id}").apply {
            mkdirs()
            resolve("${snapshot.id}.json").writeText("{\"id\":${snapshot.id}}")
            javax.imageio.ImageIO.write(
                java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png", resolve("${snapshot.id}.png"),
            )
        }
    }
    val cacheBefore = layout.sharedCache.listFiles().orEmpty().toSet()
    try {
        // Multiple same-named archives remain distinct, readable, and retained after handoff.
        val shared = mutableListOf<File>()
        withShareViewModel { vm ->
            vm.shareSnapshots(targets, SharePlatform { files ->
                assertEquals(targets.size, vm.shareProgress.value?.completed)
                assertEquals(1, files.map { it.name }.distinct().size)
                files.zip(targets).forEach { (file, snapshot) ->
                    ZipFile(file).use { zip ->
                        assertEquals(
                            "{\"id\":${snapshot.id}}",
                            zip.getInputStream(zip.getEntry("${snapshot.id}.json")).reader().readText(),
                        )
                        assertTrue(zip.getEntry("${snapshot.id}.png") != null)
                    }
                }
                shared.addAll(files)
                PlatformResult.Success(Unit)
            })
            awaitShare(vm)
        }
        assertEquals(3, shared.size)
        assertTrue(shared.all { it.isFile })
        shared.forEach { SnapshotStore.deleteArchive(it) }

        // A launch failure or unsupported platform must release every prepared ZIP.
        for (failLaunch in listOf(false, true)) {
            withShareViewModel { vm ->
                vm.shareSnapshots(targets, SharePlatform {
                    if (failLaunch) throw IOException("share chooser failed")
                    PlatformResult.Unsupported
                })
                awaitShare(vm)
            }
            assertEquals(cacheBefore, layout.sharedCache.listFiles().orEmpty().toSet())
        }

        // A later incomplete snapshot aborts the whole batch, including earlier complete archives.
        val missing = targets.last()
        val image = layout.snapshot.resolve("${missing.id}/${missing.id}.png")
        val bytes = image.readBytes()
        image.delete()
        try {
            withShareViewModel { vm ->
                vm.shareSnapshots(targets, SharePlatform { throw AssertionError("Must not share a partial batch") })
                awaitShare(vm)
            }
            assertEquals(cacheBefore, layout.sharedCache.listFiles().orEmpty().toSet())
        } finally {
            image.writeBytes(bytes)
        }

        // Both explicit cancellation and navigation-entry disposal clean already prepared files.
        for (dispose in listOf(false, true)) {
            val owner = ViewModelStore()
            val vm = SnapshotViewModel().also { owner.put("snapshot", it) }
            var launched = false
            try {
                coroutineScope {
                    val cancel = launch(start = CoroutineStart.UNDISPATCHED) {
                        vm.shareProgress.first { it != null && it.completed > 0 }
                        if (dispose) owner.clear() else vm.cancelShare()
                    }
                    vm.shareSnapshots(targets, SharePlatform {
                        launched = true
                        PlatformResult.Success(Unit)
                    })
                    // Repeated requests cannot start a second preparation while the first is active.
                    vm.shareSnapshots(targets, SharePlatform { throw AssertionError("Duplicate share") })
                    awaitShare(vm)
                    cancel.join()
                }
                assertFalse(launched)
                assertEquals(cacheBefore, layout.sharedCache.listFiles().orEmpty().toSet())
            } finally {
                owner.clear()
            }
        }
    } finally {
        targets.forEach { SnapshotStore.delete(it) }
    }
}

private suspend fun awaitShare(vm: SnapshotViewModel) {
    withTimeout(10_000) { vm.shareProgress.first { it == null } }
}

private suspend fun withShareViewModel(block: suspend (SnapshotViewModel) -> Unit) {
    val owner = ViewModelStore()
    try {
        block(SnapshotViewModel().also { owner.put("snapshot", it) })
    } finally {
        owner.clear()
    }
}

private class SharePlatform(
    private val onShare: suspend (List<File>) -> PlatformResult<Unit>,
) : SnapshotPlatformActions {
    override suspend fun share(files: List<File>) = onShare(files)
    override suspend fun ensureSavePermission(): Boolean = error("Sharing needs no save permission")
    override suspend fun save(file: File): PlatformResult<Boolean> = error("Not saving")
    override suspend fun pickImage(): li.gkd.app.storage.FileSource? = error("Not picking")
}
