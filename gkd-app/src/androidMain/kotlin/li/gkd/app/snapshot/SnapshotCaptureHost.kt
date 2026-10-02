package li.gkd.app.snapshot

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
import kotlinx.coroutines.withContext
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.a11y.currentTopActivity
import li.gkd.app.app.ActivityNames.getShowActivityId
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.data.appinfo.PackageAppCatalog.selfAppInfo
import li.gkd.app.data.captureNodeInfoList
import li.gkd.app.data.currentDeviceInfo
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.RpcError
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.platform.PlatformResult
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.platform_action_unsupported
import li.gkd.app.resources.screenshot_frame_missing
import li.gkd.app.resources.screenshot_replace_manually
import li.gkd.app.resources.service_unavailable_authorize
import li.gkd.app.resources.snapshot_app_a11y_missing
import li.gkd.app.resources.snapshot_auto_export_failed
import li.gkd.app.resources.snapshot_auto_export_permission_denied
import li.gkd.app.resources.snapshot_save_in_progress
import li.gkd.app.resources.snapshot_saved
import li.gkd.app.resources.snapshot_saved_warning
import li.gkd.app.service.ScreenshotService
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.snapshot.platform.encodeSnapshotScreenshot
import li.gkd.app.ui.option.AutomatorModeOption
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.BarUtils
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ScreenUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.px
import org.jetbrains.compose.resources.getString
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

object SnapshotCaptureHost {
    val isCapturing: Boolean get() = SnapshotCaptureRepository.isCapturing

    private class ScreenResult(
        val bytes: ByteArray,
        val status: SnapshotScreenshotStatus,
    )

    private fun createMissingScreenshotBitmap(): Bitmap {
        val bitmap = createBitmap(ScreenUtils.getWidth(), ScreenUtils.getHeight())
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 32.sp.px
                color = Color.BLUE
                textAlign = Paint.Align.CENTER
            }
            val canvas = Canvas(bitmap)
            val lines = listOf(
                Res.string.screenshot_frame_missing.getSync(),
                Res.string.screenshot_replace_manually.getSync()
            )
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
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
        }
    }

    private fun createBlankScreenshotBitmap(): Bitmap {
        val bitmap = createBitmap(ScreenUtils.getWidth(), ScreenUtils.getHeight())
        try {
            bitmap.eraseColor(Color.BLACK)
            return bitmap
        } catch (e: Throwable) {
            bitmap.recycle()
            throw e
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
        try {
            val barHeight = min(BarUtils.getStatusHeight(), mutableBitmap.height)
            for (x in 0 until mutableBitmap.width) {
                for (y in 0 until barHeight) {
                    mutableBitmap[x, y] = 0
                }
            }
            return mutableBitmap
        } catch (e: Throwable) {
            if (mutableBitmap !== bitmap) mutableBitmap.recycle()
            throw e
        }
    }

    private fun looksLikeBlankScreenshot(bitmap: Bitmap): Boolean {
        fun Bitmap.recycleIfTemporary() {
            if (this !== bitmap) recycle()
        }

        val size = 64
        val scaled = bitmap.scale(size, size, false)
        val softwareBitmap = if (scaled.config == Bitmap.Config.HARDWARE) {
            val copy = try {
                scaled.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                scaled.recycleIfTemporary()
            }
            copy ?: return false
        } else {
            scaled
        }
        val pixels = try {
            IntArray(size * size).also {
                softwareBitmap.getPixels(it, 0, size, 0, 0, size, size)
            }
        } finally {
            softwareBitmap.recycleIfTemporary()
        }
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
        var topActivity = currentTopActivity
        var waited = 0L
        while (topActivity.appId != appId && waited < 2000) {
            delay(100.milliseconds)
            topActivity = currentTopActivity
            waited += 100
        }
        return topActivity.activityId.takeIf { topActivity.appId == appId }
    }

    private suspend fun isFocusedWindowSecure(appId: String): Boolean? =
        withContext(Dispatchers.IO) {
            try {
                privilegeContextFlow.value?.isFocusedWindowSecure(appId)
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
        hideStatusBar: Boolean,
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
                A11yRuntime.screenshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d("无障碍截图失败", e)
                null
            }
        }
        val rawPicture = a11yScreenshot ?: ScreenshotService.screenshot()
        var ownedBitmap = rawPicture
        try {
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
            ownedBitmap = bitmap
            val processedBitmap = if (
                status == SnapshotScreenshotStatus.Captured &&
                hideStatusBar &&
                (forcedCropStatusBar || BarUtils.isStatusVisible() == true)
            ) {
                cropStatusBar(bitmap).also { cropped ->
                    if (cropped !== bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }
            ownedBitmap = processedBitmap
            return ScreenResult(processedBitmap.encodeSnapshotScreenshot(), status)
        } finally {
            ownedBitmap?.recycle()
        }
    }

    suspend fun capture(forcedCropStatusBar: Boolean = false): ComplexSnapshot {
        val service = A11yRuntime.service
            ?: throw RpcError(getString(Res.string.service_unavailable_authorize))
        val settings = settings.value
        val captured = try {
            SnapshotCaptureRepository.capture(settings.autoSaveSnapshotToDownloads) {
                val rootNode = A11yRuntime.getRoot(service)
                    ?: throw RpcError(getString(Res.string.snapshot_app_a11y_missing))
                val appId = rootNode.packageName.toString()
                val screenHeight = ScreenUtils.getHeight()
                val screenWidth = ScreenUtils.getWidth()
                val isLandscape = ScreenUtils.isLandscape()
                coroutineScope {
                    val nodes = async(Dispatchers.IO) { captureNodeInfoList(rootNode) }
                    val activityId = async(Dispatchers.IO) { resolveActivityId(appId) }
                    val screenshot = async(Dispatchers.Default) {
                        captureScreen(
                            appId,
                            service.mode,
                            forcedCropStatusBar,
                            settings.hideSnapshotStatusBar,
                        )
                    }
                    val screen = screenshot.await()
                    SnapshotCaptureInput(
                        appId = appId,
                        activityId = activityId.await(),
                        screenHeight = screenHeight,
                        screenWidth = screenWidth,
                        isLandscape = isLandscape,
                        nodes = nodes.await(),
                        appInfo = AppInfoRepository.snapshot?.apps?.get(appId),
                        gkdAppInfo = selfAppInfo,
                        device = currentDeviceInfo(),
                        screenshot = screen.bytes,
                        screenshotStatus = screen.status,
                    )
                }
            }
        } catch (_: SnapshotCaptureBusyException) {
            throw RpcError(getString(Res.string.snapshot_save_in_progress))
        }
        val snapshot = captured.snapshot
        val exportDetail = when (val exported = captured.autoExport) {
            SnapshotAutoExportResult.Disabled -> null
            SnapshotAutoExportResult.PermissionDenied ->
                getString(Res.string.snapshot_auto_export_permission_denied)

            is SnapshotAutoExportResult.Completed -> when (exported.result) {
                is PlatformResult.Success -> null
                PlatformResult.Unsupported -> getString(Res.string.platform_action_unsupported)
            }

            is SnapshotAutoExportResult.Failed -> {
                LogUtils.d("自动保存快照至下载失败", exported.cause)
                getString(
                    Res.string.snapshot_auto_export_failed,
                    exported.cause.displayMessage()
                )
            }
        }
        val detail = listOfNotNull(captured.screenshotStatus.detailText(), exportDetail)
            .joinToString(" · ").takeIf { it.isNotEmpty() }
        // Notification delivery does not change the already committed capture / HTTP result.
        try {
            NotificationCatalog.snapshotSaved(
                appName = snapshot.appInfo?.name ?: snapshot.appId,
                activityId = getShowActivityId(snapshot.appId, snapshot.activityId),
                screenshotStatus = captured.screenshotStatus,
                savedToDownloads = (captured.autoExport as? SnapshotAutoExportResult.Completed)
                    ?.result is PlatformResult.Success,
                exportDetail = exportDetail,
            ).post()
            ToastUtils.show(
                if (detail == null) getString(Res.string.snapshot_saved)
                else getString(Res.string.snapshot_saved_warning, detail), forced = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.d("快照已保存，发送提示失败", e)
        }
        return snapshot
    }
}
