package li.songe.gkd.debug

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import li.songe.gkd.data.ComplexSnapshot
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.info2nodeList
import li.songe.gkd.db.DbSet
import li.songe.gkd.notif.snapshotNotif
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.getAndUpdateCurrentRules
import li.songe.gkd.service.safeActiveWindow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.autoMk
import li.songe.gkd.util.drawTextToBitmap
import li.songe.gkd.util.keepNullJson
import li.songe.gkd.util.sharedDir
import li.songe.gkd.util.snapshotFolder
import li.songe.gkd.util.toast
import java.io.File
import kotlin.math.min

object SnapshotExt {

    private fun snapshotParentPath(id: Long) = snapshotFolder.resolve(id.toString())
    fun snapshotFile(id: Long) = snapshotParentPath(id).resolve("${id}.json")
    fun screenshotFile(id: Long) = snapshotParentPath(id).resolve("${id}.png")

    suspend fun snapshotZipFile(
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
        val file = sharedDir.resolve(filename)
        if (file.exists()) {
            file.delete()
        }
        withContext(Dispatchers.IO) {
            ZipUtils.zipFiles(
                listOf(
                    snapshotFile(snapshotId),
                    screenshotFile(snapshotId)
                ),
                file
            )
        }
        return file
    }

    fun removeSnapshot(id: Long) {
        snapshotParentPath(id).apply {
            if (exists()) {
                deleteRecursively()
            }
        }
    }

    private fun emptyScreenBitmap(text: String): Bitmap {
        return createBitmap(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight()).apply {
            drawTextToBitmap(text, this)
        }
    }

    private suspend fun screenshot(): Bitmap? {
        return A11yService.screenshot() ?: ScreenshotService.screenshot()
    }

    private fun cropBitmapStatusBar(bitmap: Bitmap): Bitmap {
        val barHeight = BarUtils.getStatusBarHeight()
        val tempBp = bitmap.run {
            if (!isMutable || config == Bitmap.Config.HARDWARE) {
                return copy(Bitmap.Config.ARGB_8888, true)
            } else {
                this
            }
        }
        for (x in 0 until tempBp.width) {
            for (y in 0 until min(barHeight, tempBp.height)) {
                tempBp[x, y] = 0
            }
        }
        return tempBp
    }

    private val captureLoading = MutableStateFlow(false)
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

            val (snapshot, bitmap) = coroutineScope {
                val d1 = async(Dispatchers.IO) {
                    ComplexSnapshot(
                        id = System.currentTimeMillis(),
                        appId = rootNode.packageName.toString(),
                        activityId = getAndUpdateCurrentRules().topActivity.activityId,
                        screenHeight = ScreenUtils.getScreenHeight(),
                        screenWidth = ScreenUtils.getScreenWidth(),
                        isLandscape = ScreenUtils.isLandscape(),
                        nodes = info2nodeList(rootNode)
                    )
                }
                val d2 = async(Dispatchers.IO) {
                    if (skipScreenshot) {
                        emptyScreenBitmap("跳过截图\n请自行替换")
                    } else {
                        screenshot() ?: emptyScreenBitmap("无截图权限\n请自行替换")
                    }.let {
                        if (storeFlow.value.hideSnapshotStatusBar) {
                            cropBitmapStatusBar(it)
                        } else {
                            it
                        }
                    }
                }
                d1.await() to d2.await()
            }
            withContext(Dispatchers.IO) {
                snapshotParentPath(snapshot.id).autoMk()
                screenshotFile(snapshot.id).outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                val text = keepNullJson.encodeToString(snapshot)
                snapshotFile(snapshot.id).writeText(text)
                DbSet.snapshotDao.insert(snapshot.toSnapshot())
            }
            toast("快照成功")
            val desc = snapshot.appInfo?.name ?: snapshot.appId
            snapshotNotif.copy(
                text = if (desc != null) {
                    "快照「$desc」已保存至记录"
                } else {
                    snapshotNotif.text
                }
            ).notifySelf()
            return snapshot
        } finally {
            captureLoading.value = false
        }
    }
}