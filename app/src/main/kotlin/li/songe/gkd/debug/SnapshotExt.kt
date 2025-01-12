package li.songe.gkd.debug

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.graphics.set
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.data.ComplexSnapshot
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.info2nodeList
import li.songe.gkd.data.toSnapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.notif.notify
import li.songe.gkd.notif.snapshotNotif
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.getAndUpdateCurrentRules
import li.songe.gkd.service.safeActiveWindow
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.keepNullJson
import li.songe.gkd.util.snapshotFolder
import li.songe.gkd.util.snapshotZipDir
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.toast
import java.io.File
import kotlin.math.min

object SnapshotExt {

    private fun getSnapshotParentPath(snapshotId: Long) =
        "${snapshotFolder.absolutePath}/${snapshotId}"

    fun getSnapshotPath(snapshotId: Long) =
        "${getSnapshotParentPath(snapshotId)}/${snapshotId}.json"

    fun getScreenshotPath(snapshotId: Long) =
        "${getSnapshotParentPath(snapshotId)}/${snapshotId}.png"

    suspend fun getSnapshotZipFile(
        snapshotId: Long,
        appId: String? = null,
        activityId: String? = null
    ): File {
        val filename = if (appId != null) {
            val name =
                appInfoCacheFlow.value[appId]?.name?.filterNot { c -> c in "\\/:*?\"<>|" || c <= ' ' }
            if (activityId != null) {
                "${(name ?: appId).take(20)}_${
                    activityId.split('.').last().take(40)
                }-${snapshotId}.zip"
            } else {
                "${(name ?: appId).take(20)}-${snapshotId}.zip"
            }
        } else {
            "${snapshotId}.zip"
        }
        val file = snapshotZipDir.resolve(filename)
        if (file.exists()) {
            return file
        }
        withContext(Dispatchers.IO) {
            ZipUtils.zipFiles(
                listOf(
                    getSnapshotPath(snapshotId), getScreenshotPath(snapshotId)
                ), file.absolutePath
            )
        }
        return file
    }

    fun removeAssets(id: Long) {
        File(getSnapshotParentPath(id)).apply {
            if (exists()) {
                deleteRecursively()
            }
        }
    }

    private val captureLoading = MutableStateFlow(false)

    private fun createComplexSnapshot(rootNode: AccessibilityNodeInfo): ComplexSnapshot {
        val currentActivityId = getAndUpdateCurrentRules().topActivity.activityId

        return ComplexSnapshot(
            id = System.currentTimeMillis(),

            appId = rootNode.packageName.toString(),
            activityId = currentActivityId,

            screenHeight = ScreenUtils.getScreenHeight(),
            screenWidth = ScreenUtils.getScreenWidth(),
            isLandscape = ScreenUtils.isLandscape(),

            nodes = info2nodeList(rootNode)
        )
    }

    suspend fun captureSnapshot(skipScreenshot: Boolean = false): ComplexSnapshot {
        if (!A11yService.isRunning.value) {
            throw RpcError("无障碍不可用,请先授权")
        }
        if (captureLoading.value) {
            throw RpcError("正在保存快照,不可重复操作")
        }
        captureLoading.value = true
        try {
            val rootNode =
                A11yService.instance?.safeActiveWindow
                    ?: throw RpcError("当前应用没有无障碍信息,捕获失败")
            if (storeFlow.value.showSaveSnapshotToast) {
                toast("正在保存快照...")
            }
            val snapshotDef =
                coroutineScope { async(Dispatchers.IO) { createComplexSnapshot(rootNode) } }
            val bitmapDef = coroutineScope {// TODO 也许在分屏模式下可能需要处理
                async(Dispatchers.IO) {
                    if (skipScreenshot) {
                        LogUtils.d("跳过截屏，即将使用空白图片")
                        Bitmap.createBitmap(
                            ScreenUtils.getScreenWidth(),
                            ScreenUtils.getScreenHeight(),
                            Bitmap.Config.ARGB_8888
                        )
                    } else {
                        A11yService.currentScreenshot() ?: withTimeoutOrNull(3_000) {
                            if (!ScreenshotService.isRunning.value) {
                                return@withTimeoutOrNull null
                            }
                            ScreenshotService.screenshot()
                        } ?: Bitmap.createBitmap(
                            ScreenUtils.getScreenWidth(),
                            ScreenUtils.getScreenHeight(),
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            LogUtils.d("截屏不可用，即将使用空白图片")
                        }
                    }
                }
            }

            var bitmap = bitmapDef.await()
            if (storeFlow.value.hideSnapshotStatusBar) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                for (x in 0 until bitmap.width) {
                    for (y in 0 until min(BarUtils.getStatusBarHeight(), bitmap.height)) {
                        bitmap[x, y] = 0
                    }
                }
            }
            val snapshot = snapshotDef.await()

            withContext(Dispatchers.IO) {
                File(getSnapshotParentPath(snapshot.id)).apply { if (!exists()) mkdirs() }
                File(getScreenshotPath(snapshot.id)).outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                val text = keepNullJson.encodeToString(snapshot)
                File(getSnapshotPath(snapshot.id)).writeText(text)
                DbSet.snapshotDao.insert(snapshot.toSnapshot())
            }
            toast("快照成功")
            val desc = snapshot.appInfo?.name ?: snapshot.appId
            snapshotNotif.copy(
                text = if (desc != null) {
                    "快照[$desc]已保存至记录"
                } else {
                    snapshotNotif.text
                }
            ).notify()
            return snapshot
        } finally {
            captureLoading.value = false
        }
    }
}