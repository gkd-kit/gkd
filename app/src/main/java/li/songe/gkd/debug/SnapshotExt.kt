package li.songe.gkd.debug

import android.graphics.Bitmap
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.app
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.Singleton
import java.io.File

object SnapshotExt {
    private val snapshotDir by lazy {
        app.getExternalFilesDir("snapshot")!!.apply { if (!exists()) mkdir() }
    }

    private val emptyBitmap by lazy {
        Bitmap.createBitmap(
            ScreenUtils.getScreenWidth(),
            ScreenUtils.getScreenHeight(),
            Bitmap.Config.ARGB_8888
        )
    }

    fun getSnapshotParentPath(snapshotId: Long) =
        "${snapshotDir.absolutePath}/${snapshotId}"

    fun getSnapshotPath(snapshotId: Long) =
        "${getSnapshotParentPath(snapshotId)}/${snapshotId}.json"

    fun getScreenshotPath(snapshotId: Long) =
        "${getSnapshotParentPath(snapshotId)}/${snapshotId}.png"

    fun getSnapshotIds(): List<Long> {
        return snapshotDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { f -> f.name.toLongOrNull() } ?: emptyList()
    }

    suspend fun getSnapshotZipFile(snapshotId: Long): File {
        val file = File(getSnapshotParentPath(snapshotId) + "/${snapshotId}.zip")
        if (!file.exists()) {
            withContext(Dispatchers.IO) {
                ZipUtils.zipFiles(
                    listOf(
                        getSnapshotPath(snapshotId),
                        getScreenshotPath(snapshotId)
                    ), file.absolutePath
                )
            }
        }
        return file
    }

    fun remove(id: Long) {
        File(getSnapshotParentPath(id)).apply {
            if (exists()) {
                deleteRecursively()
            }
        }
    }

    suspend fun captureSnapshot(): Snapshot {
        if (!GkdAbService.isRunning()) {
            throw RpcError("无障碍不可用")
        }

        val snapshotDef = coroutineScope { async(Dispatchers.IO) { Snapshot.current() } }
        val bitmapDef = coroutineScope {
            async(Dispatchers.IO) {
                GkdAbService.currentScreenshot() ?: withTimeoutOrNull(3_000) {
                    if (!ScreenshotService.isRunning()) {
                        return@withTimeoutOrNull null
                    }
                    ScreenshotService.screenshot()
                } ?: emptyBitmap.apply {
                    LogUtils.d("截屏不可用，即将使用空白图片")
                }
            }
        }

        val bitmap = bitmapDef.await()
        val snapshot = snapshotDef.await()

        withContext(Dispatchers.IO) {
            File(getSnapshotParentPath(snapshot.id)).apply { if (!exists()) mkdirs() }
            val stream =
                File(getScreenshotPath(snapshot.id)).outputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            val text = Singleton.json.encodeToString(snapshot)
            File(getSnapshotPath(snapshot.id)).writeText(text)
            DbSet.snapshotDao.insert(snapshot)
        }
        return snapshot
    }
}