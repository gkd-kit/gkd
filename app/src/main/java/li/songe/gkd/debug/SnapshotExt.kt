package li.songe.gkd.debug

import android.graphics.Bitmap
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import li.songe.gkd.App
import li.songe.gkd.accessibility.GkdAbService
import li.songe.gkd.util.Singleton
import java.io.File

object SnapshotExt {
    private val snapshotDir by lazy {
        App.context.getExternalFilesDir("snapshot")!!.apply { if (!exists()) mkdir() }
    }

    private fun getSnapshotParentPath(snapshotId: Long) =
        "${snapshotDir.absolutePath}/${snapshotId}"

    fun getSnapshotPath(snapshotId: Long) =
        "${getSnapshotParentPath(snapshotId)}/${snapshotId}.json"

    fun getScreenshotPath(snapshotId: Long) =
        "${getSnapshotParentPath(snapshotId)}/${snapshotId}.png"

    fun getSnapshotIds(): List<Long> {
        return snapshotDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { f -> f.name.toLongOrNull() } ?: emptyList()
    }

    suspend fun captureSnapshot(): Snapshot {
        if (!GkdAbService.isRunning()) {
            throw RpcError("无障碍不可用")
        }
        if (!ScreenshotService.isRunning()) {
            LogUtils.d("截屏不可用，即将使用空白图片")
        }
        val snapshot = Snapshot.current()
        val bitmap = withTimeoutOrNull(3_000) {
            ScreenshotService.screenshot()
        } ?: Bitmap.createBitmap(
            snapshot.screenWidth,
            snapshot.screenHeight,
            Bitmap.Config.ARGB_8888
        )
        withContext(Dispatchers.IO) {
            File(getSnapshotParentPath(snapshot.id)).apply { if (!exists()) mkdirs() }
            val stream =
                File(getScreenshotPath(snapshot.id)).outputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            val text = Singleton.json.encodeToString(snapshot)
            File(getSnapshotPath(snapshot.id)).writeText(text)
        }
        return snapshot
    }
}