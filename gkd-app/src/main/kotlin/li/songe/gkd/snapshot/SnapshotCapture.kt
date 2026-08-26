package li.songe.gkd.snapshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.set
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.data.ComplexSnapshot
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.info2nodeList
import li.songe.gkd.notif.NotificationCatalog
import li.songe.gkd.priv.privilegeContextFlow
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.AutomatorModeOption
import li.songe.gkd.util.BarUtils
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.SystemDownloads
import li.songe.gkd.util.getShowActivityId
import li.songe.gkd.util.px
import li.songe.gkd.util.toast
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

object SnapshotCapture {
    private val captureMutex = Mutex()
    val isCapturing: Boolean
        get() = captureMutex.isLocked

    private data class ScreenResult(
        val bitmap: Bitmap,
        val status: SnapshotScreenshotStatus,
    )

    private fun createMissingScreenshotBitmap(): Bitmap {
        val bitmap = createBitmap(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 32.sp.px
            color = Color.BLUE
            textAlign = Paint.Align.CENTER
        }
        val canvas = Canvas(bitmap)
        val lines = listOf("未获取到屏幕画面", "请手动替换截图")
        lines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                bitmap.width / 2f,
                (bitmap.height / 2f) +
                        (index - lines.size / 2f) * (paint.textSize + 4.sp.px),
                paint,
            )
        }
        return bitmap
    }

    private fun createBlankScreenshotBitmap(): Bitmap {
        return createBitmap(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight()).apply {
            eraseColor(Color.BLACK)
        }
    }

    private fun cropStatusBar(bitmap: Bitmap): Bitmap {
        val mutableBitmap = bitmap.run {
            if (!isMutable || config == Bitmap.Config.HARDWARE) {
                copy(Bitmap.Config.ARGB_8888, true)
            } else {
                this
            }
        }
        val barHeight = min(BarUtils.getStatusBarHeight(), mutableBitmap.height)
        for (x in 0 until mutableBitmap.width) {
            for (y in 0 until barHeight) {
                mutableBitmap[x, y] = 0
            }
        }
        return mutableBitmap
    }

    private fun looksLikeBlankScreenshot(bitmap: Bitmap): Boolean {
        fun Bitmap.recycleIfTemporary() {
            if (this !== bitmap) recycle()
        }

        val size = 64
        val scaled = bitmap.scale(size, size, false)
        val softwareBitmap = if (scaled.config == Bitmap.Config.HARDWARE) {
            val copy = scaled.copy(Bitmap.Config.ARGB_8888, false)
            scaled.recycleIfTemporary()
            copy ?: return false
        } else {
            scaled
        }
        val pixels = IntArray(size * size)
        softwareBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        softwareBitmap.recycleIfTemporary()
        val ignoredEdge = (size * 0.08).toInt()
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        var nearBlackCount = 0
        val step = 2
        for (y in ignoredEdge until size - ignoredEdge step step) {
            for (x in ignoredEdge until size - ignoredEdge step step) {
                val pixel = pixels[y * size + x]
                val red = (pixel shr 16) and 0xff
                val green = (pixel shr 8) and 0xff
                val blue = pixel and 0xff
                val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
                sum += luminance
                sumSq += luminance * luminance
                count++
                if (luminance < 10) nearBlackCount++
            }
        }
        if (count == 0) return false
        val mean = sum / count
        val variance = sumSq / count - mean * mean
        val blackRatio = nearBlackCount.toDouble() / count
        return variance < 15.0 && blackRatio > 0.85 && mean < 15.0
    }

    private suspend fun resolveActivityId(appId: String): String? {
        privilegeContextFlow.value?.run {
            topCpn()?.className
        }?.let { return it }
        var topActivity = topActivityFlow.value
        var waited = 0L
        while (topActivity.appId != appId && waited < 2000) {
            delay(100.milliseconds)
            topActivity = topActivityFlow.value
            waited += 100
        }
        return topActivity.activityId.takeIf { topActivity.appId == appId }
    }

    private suspend fun isFocusedWindowSecure(appId: String): Boolean? =
        withContext(Dispatchers.IO) {
            try {
                privilegeContextFlow.value?.run {
                    wmManager.isFocusedWindowSecure(appId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d("读取前台窗口 FLAG_SECURE 失败", e)
                null
            }
        }

    private suspend fun captureScreen(
        appId: String,
        automatorMode: AutomatorModeOption,
        forcedCropStatusBar: Boolean,
    ): ScreenResult {
        // Android 14+ 的部分 ROM（已在 Android 16 HyperOS 上复现）不会在 FLAG_SECURE
        // 窗口下回调 IWindowManager.captureDisplay 的 listener，读取 buffer 会等待系统 4 秒后超时。
        // 自动化模式先检查窗口标志，命中后跳过特权截图，避免无意义的等待。
        val checkSecureBeforeCapture =
            automatorMode == AutomatorModeOption.AutomationMode && AndroidTarget.UPSIDE_DOWN_CAKE
        val focusedWindowSecure = if (checkSecureBeforeCapture) {
            isFocusedWindowSecure(appId)
        } else {
            null
        }
        val a11yScreenshot = if (focusedWindowSecure == true) {
            null
        } else {
            try {
                A11yRuleEngine.screenshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d("无障碍截图失败", e)
                null
            }
        }
        val rawPicture = a11yScreenshot ?: ScreenshotService.screenshot()
        val (bitmap, status) = when {
            rawPicture == null && focusedWindowSecure == true -> {
                createBlankScreenshotBitmap() to SnapshotScreenshotStatus.LikelyProtected
            }

            rawPicture == null -> {
                createMissingScreenshotBitmap() to SnapshotScreenshotStatus.Unavailable
            }

            looksLikeBlankScreenshot(rawPicture) -> {
                val secure = if (checkSecureBeforeCapture) {
                    focusedWindowSecure
                } else {
                    isFocusedWindowSecure(appId)
                }
                val status = if (secure == false) {
                    SnapshotScreenshotStatus.Captured
                } else {
                    SnapshotScreenshotStatus.LikelyProtected
                }
                rawPicture to status
            }

            else -> {
                rawPicture to SnapshotScreenshotStatus.Captured
            }
        }
        val processedBitmap = if (
            status == SnapshotScreenshotStatus.Captured &&
            storeFlow.value.hideSnapshotStatusBar &&
            (forcedCropStatusBar || BarUtils.checkStatusBarVisible() == true)
        ) {
            cropStatusBar(bitmap).also { cropped ->
                if (cropped !== bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }
        return ScreenResult(processedBitmap, status)
    }

    suspend fun capture(forcedCropStatusBar: Boolean = false): ComplexSnapshot {
        val engine = A11yRuleEngine.instance ?: throw RpcError("服务不可用，请先授权")
        if (!captureMutex.tryLock()) {
            throw RpcError("正在保存快照，不可重复操作")
        }
        try {
            val rootNode = engine.safeActiveWindow
                ?: throw RpcError("当前应用没有无障碍信息，捕获失败")
            val snapshotId = System.currentTimeMillis()
            val appId = rootNode.packageName.toString()
            val screenHeight = ScreenUtils.getScreenHeight()
            val screenWidth = ScreenUtils.getScreenWidth()
            val isLandscape = ScreenUtils.isLandscape()
            val (snapshot, screenResult) = coroutineScope {
                val nodes = async(Dispatchers.IO) { info2nodeList(rootNode) }
                val activityId = async(Dispatchers.IO) { resolveActivityId(appId) }
                val capturedScreen = async(Dispatchers.Default) {
                    captureScreen(appId, engine.service.mode, forcedCropStatusBar)
                }
                val result = capturedScreen.await()
                try {
                    ComplexSnapshot(
                        id = snapshotId,
                        appId = appId,
                        activityId = activityId.await(),
                        screenHeight = screenHeight,
                        screenWidth = screenWidth,
                        isLandscape = isLandscape,
                        nodes = nodes.await(),
                    ) to result
                } catch (e: Throwable) {
                    result.bitmap.recycle()
                    throw e
                }
            }

            try {
                SnapshotStore.save(snapshot, screenResult.bitmap)
            } finally {
                screenResult.bitmap.recycle()
            }
            val savedToDownloads = if (
                storeFlow.value.autoSaveSnapshotToDownloads && SystemDownloads.canSave()
            ) {
                try {
                    val archive = SnapshotStore.createArchive(
                        snapshot.id,
                        snapshot.appId,
                        snapshot.activityId,
                    )
                    try {
                        SystemDownloads.save(archive)
                    } finally {
                        SnapshotStore.deleteArchive(archive)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtils.d("自动保存快照至下载失败", e)
                    false
                }
            } else {
                false
            }
            val appName = snapshot.appInfo?.name ?: snapshot.appId
            NotificationCatalog.snapshotSaved(
                appName = appName,
                activityId = getShowActivityId(snapshot.appId, snapshot.activityId),
                screenshotStatus = screenResult.status,
                savedToDownloads = savedToDownloads,
            ).post()
            val statusDetail = screenResult.status.detailText()
            val toastText = if (statusDetail == null) {
                "快照已保存"
            } else {
                "快照已保存 ($statusDetail)"
            }
            toast(toastText, forced = true)
            return snapshot
        } finally {
            captureMutex.unlock()
        }
    }
}
