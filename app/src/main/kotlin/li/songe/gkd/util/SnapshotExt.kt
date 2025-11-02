package li.songe.gkd.util

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import li.songe.gkd.a11y.TopActivity
import li.songe.gkd.a11y.screenshot
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.data.ComplexSnapshot
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.info2nodeList
import li.songe.gkd.db.DbSet
import li.songe.gkd.notif.snapshotNotif
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.store.storeFlow
import java.io.File
import kotlin.math.min

object SnapshotExt {

    private fun snapshotParentPath(id: Long) = snapshotFolder.resolve(id.toString())
    fun snapshotFile(id: Long) = snapshotParentPath(id).resolve("${id}.json")
    private fun minSnapshotFile(id: Long): File {
        return snapshotParentPath(id).resolve("${id}.min.json")
    }

    suspend fun getMinSnapshot(id: Long): JsonObject {
        val f = minSnapshotFile(id)
        if (!f.exists()) {
            val text = withContext(Dispatchers.IO) { snapshotFile(id).readText() }
            val snapshotJson = withContext(Dispatchers.Default) {
                // #1185
                json.decodeFromString<JsonObject>(text)
            }
            val minSnapshot = JsonObject(snapshotJson.toMutableMap().apply {
                this["nodes"] = JsonArray(emptyList())
            })
            withContext(Dispatchers.IO) {
                f.writeText(keepNullJson.encodeToString(minSnapshot))
            }
            return minSnapshot
        }
        val text = withContext(Dispatchers.IO) { f.readText() }
        return withContext(Dispatchers.Default) {
            json.decodeFromString<JsonObject>(text)
        }
    }

    fun screenshotFile(id: Long) = snapshotParentPath(id).resolve("${id}.png")

    suspend fun snapshotZipFile(
        snapshotId: Long,
        appId: String? = null,
        activityId: String? = null
    ): File {
        val filename = if (appId != null) {
            val name =
                appInfoMapFlow.value[appId]?.name?.filterNot { c -> c in "\\/:*?\"<>|" || c <= ' ' }
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
        return A11yService.instance?.screenshot() ?: ScreenshotService.screenshot()
    }

    private fun cropBitmapStatusBar(bitmap: Bitmap): Bitmap {
        val tempBp = bitmap.run {
            if (!isMutable || config == Bitmap.Config.HARDWARE) {
                copy(Bitmap.Config.ARGB_8888, true)
            } else {
                this
            }
        }
        val barHeight = min(BarUtils.getStatusBarHeight(), tempBp.height)
        for (x in 0 until tempBp.width) {
            for (y in 0 until barHeight) {
                tempBp[x, y] = 0
            }
        }
        return tempBp
    }

    private val captureLoading = MutableStateFlow(false)
    suspend fun captureSnapshot(skipScreenshot: Boolean = false): ComplexSnapshot {
        if (!A11yService.isRunning.value) {
            throw RpcError("无障碍不可用，请先授权")
        }
        if (captureLoading.value) {
            throw RpcError("正在保存快照，不可重复操作")
        }
        captureLoading.value = true
        try {
            val rootNode =
                A11yService.instance?.safeActiveWindow
                    ?: throw RpcError("当前应用没有无障碍信息，捕获失败")
            if (storeFlow.value.showSaveSnapshotToast) {
                toast("正在保存快照...")
            }

            val (snapshot, bitmap) = coroutineScope {
                val d1 = async(Dispatchers.IO) {
                    val appId = rootNode.packageName.toString()
                    var activityId = shizukuContextFlow.value.topCpn()?.className
                    if (activityId == null) {
                        var topActivity = topActivityFlow.value
                        var i = 0L
                        while (topActivity.appId != appId) {
                            delay(100)
                            topActivity = topActivityFlow.value
                            i += 100
                            if (i >= 2000) {
                                topActivity = TopActivity(appId = appId)
                                break
                            }
                        }
                        activityId = topActivity.activityId
                    }
                    ComplexSnapshot(
                        id = System.currentTimeMillis(),
                        appId = appId,
                        activityId = activityId,
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
                snapshotFile(snapshot.id).writeText(keepNullJson.encodeToString(snapshot))
                minSnapshotFile(snapshot.id).writeText(
                    keepNullJson.encodeToString(
                        snapshot.copy(
                            nodes = emptyList()
                        )
                    )
                )
                DbSet.snapshotDao.insert(snapshot.toSnapshot())
            }
            toast("快照成功")
            val desc = snapshot.appInfo?.name ?: snapshot.appId
            snapshotNotif.copy(text = "快照「$desc」已保存至记录").notifySelf()
            return snapshot
        } finally {
            captureLoading.value = false
        }
    }
}