package li.songe.gkd.util

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.a11y.TopActivity
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.data.ComplexSnapshot
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.info2nodeList
import li.songe.gkd.db.DbSet
import li.songe.gkd.notif.snapshotNotif
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

    @Suppress("SameParameterValue")
    private fun emptyScreenBitmap(text: String): Bitmap {
        return createBitmap(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight()).apply {
            drawTextToBitmap(text, this)
        }
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
    // 截图三种状态
    private enum class ScreenWhy {
        Pass,
        NotHave,
        Block,
    }
    // App拒绝提供画面判定逻辑
    private fun isAppProtected(bitmap: Bitmap): Boolean {
        fun Bitmap.recycleIfTemp() { if (this !== bitmap) recycle() }
        // 缩小图片
        val size = 64
        val scaled = bitmap.scale(size, size, false)

        /* 强制转为 ARGB_8888（软件位图）
        部分设备,Android版本scale()返回仍是HARDWARE
        bitmap在 gpu内存,cpu无法直接读,会崩溃
         */
        val softBitmap = if (scaled.config == Bitmap.Config.HARDWARE) {
            val copy = scaled.copy(Bitmap.Config.ARGB_8888, false)
            scaled.recycleIfTemp()
            copy ?: return false  // copy 失败（极端 OOM）直接返回，不继续执行
        } else {
            scaled
        }
        //  像素一次性读取到数组
        val pixels = IntArray(size * size)
        softBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        softBitmap.recycleIfTemp()
        val ignore = (size * 0.08).toInt()  //  忽略图片边缘
        // 统计变量
        var sum = 0.0  //  亮度总和
        var sumSq = 0.0  //  平方总和
        var count = 0 // 样本数量
        //  统计极值像素占比
        var nearBlackCount = 0
        // 采样
        val step = 2 //隔一个像素取样
        for (y in ignore until size - ignore step step) {  // ignore(忽略边缘)
            for (x in ignore until size - ignore step step) {
                // 提取RGB
                val p = pixels[y * size + x]
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                // 计算亮度
                val l = 0.299 * r + 0.587 * g + 0.114 * b
                // 统计
                sum += l
                sumSq += l * l
                count++
                if (l < 10) nearBlackCount++
            }
        }
        // 防止除零
        if (count == 0) return false
        // 平均值和方差计算
        val mean = sum / count
        val variance = sumSq / count - mean * mean

        // 极值(纯黑)像素占比
        val blackRatio = nearBlackCount.toDouble() / count
        // 判断条件拆分,低方差+像素高度集中在极端值
        val isNearlyUniform = variance < 15.0  // 放宽，包容轻微噪点
        val isDominantlyBlack = blackRatio > 0.85 && mean < 15.0
        // 判断值设定
        return isNearlyUniform && (isDominantlyBlack)
    }
    private val captureLoading = MutableStateFlow(false)
    suspend fun captureSnapshot(forcedCropStatusBar: Boolean = false): ComplexSnapshot {
        if (A11yRuleEngine.instance == null) {
            throw RpcError("服务不可用，请先授权")
        }
        if (captureLoading.value) {
            throw RpcError("正在保存快照，不可重复操作")
        }
        captureLoading.value = true
        try {
            val rootNode =
                A11yRuleEngine.instance?.safeActiveWindow
                    ?: throw RpcError("当前应用没有无障碍信息，捕获失败")
            if (storeFlow.value.showSaveSnapshotToast) {
                toast("正在保存快照...", forced = true)
            }
            val (snapshot, screenResult) = coroutineScope {  // 快照数据+截图(图片 && 状态)
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
                    val rawPicture =  // 获取原始图片
                        A11yRuleEngine.screenshot()  // 无障碍
                        ?: ScreenshotService.screenshot() // 截图服务

                    val (finalBitmap, status) = when {
                        rawPicture == null -> {
                            emptyScreenBitmap("无截图权限\n请自行替换") to ScreenWhy.NotHave
                        }
                        isAppProtected(rawPicture) -> {
                            rawPicture to ScreenWhy.Block
                        }
                        else -> {
                            rawPicture to ScreenWhy.Pass
                        }
                    }

                    val processedBitmap = if (status == ScreenWhy.Pass &&
                        storeFlow.value.hideSnapshotStatusBar && (forcedCropStatusBar || BarUtils.checkStatusBarVisible() == true)) {
                          cropBitmapStatusBar(finalBitmap)
                    } else {
                        finalBitmap
                    }
                    processedBitmap to status
                }
                d1.await() to d2.await()
            }

            val (bitmap, currentStatus) = screenResult // 拆开(图片+状态)
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
            val tip = when (currentStatus) {
                ScreenWhy.NotHave -> "快照成功 (无截图)"
                ScreenWhy.Block -> "快照成功 (应用可能禁止截图)"
                ScreenWhy.Pass -> "快照成功"
            }
            toast(tip, forced = true)
            val desc = snapshot.appInfo?.name ?: snapshot.appId
            snapshotNotif.copy(text = "快照「$desc」已保存至记录").notifySelf()
            return snapshot
        } finally {
            captureLoading.value = false
        }
    }
}