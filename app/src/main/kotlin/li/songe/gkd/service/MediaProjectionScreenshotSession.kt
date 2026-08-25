package li.songe.gkd.service

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import li.songe.gkd.app
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.isFullTransparent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// https://github.com/npes87184/ScreenShareTile/blob/master/app/src/main/java/com/npes87184/screenshottile/ScreenshotService.kt

class MediaProjectionScreenshotSession(
    private val screenshotIntent: Intent,
    private val onProjectionStop: () -> Unit,
) : AutoCloseable {
    private val handlerThread = HandlerThread("gkd-screenshot").apply { start() }
    private val handler = Handler(handlerThread.looper)

    @Volatile
    private var closed = false
    private var projectionStopped = false
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var activeImageReader: ImageReader? = null
    private var activeContinuation: CancellableContinuation<Bitmap>? = null

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            projectionStopped = true
            val continuation = activeContinuation
            releaseActiveCapture()
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.unregisterCallback(this)
            mediaProjection = null
            if (continuation?.isActive == true) {
                continuation.resumeWithException(
                    IllegalStateException("截屏授权已失效")
                )
            }
            onProjectionStop()
        }
    }

    private val width: Int
        get() = ScreenUtils.getScreenWidth()
    private val height: Int
        get() = ScreenUtils.getScreenHeight()
    private val dpi: Int
        get() = ScreenUtils.getScreenDensityDpi()

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        if (!handler.post(::closeOnHandler)) {
            handlerThread.quitSafely()
        }
    }

    private fun closeOnHandler() {
        val continuation = activeContinuation
        releaseActiveCapture()
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.let { projection ->
            projection.unregisterCallback(mediaProjectionCallback)
            projection.stop()
        }
        mediaProjection = null
        if (continuation?.isActive == true) {
            continuation.resumeWithException(
                IllegalStateException("截屏服务已停止")
            )
        }
        handlerThread.quitSafely()
    }

    suspend fun capture(): Bitmap = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            handler.post {
                if (activeContinuation === continuation) {
                    releaseActiveCapture()
                }
            }
        }
        if (!handler.post { startCapture(continuation) } && continuation.isActive) {
            continuation.resumeWithException(
                IllegalStateException("截屏线程不可用")
            )
        }
    }

    private fun startCapture(continuation: CancellableContinuation<Bitmap>) {
        if (!continuation.isActive) return
        if (closed || projectionStopped) {
            continuation.resumeWithException(
                IllegalStateException("截屏服务不可用")
            )
            return
        }
        if (activeContinuation != null) {
            continuation.resumeWithException(
                IllegalStateException("正在截取屏幕")
            )
            return
        }

        var imageReader: ImageReader? = null
        try {
            val captureWidth = width
            val captureHeight = height
            val captureDpi = dpi
            val projection = mediaProjection ?: (
                app.mediaProjectionManager.getMediaProjection(
                    RESULT_OK,
                    screenshotIntent,
                ) ?: throw IllegalStateException("获取截屏授权失败")
                ).also {
                    it.registerCallback(mediaProjectionCallback, handler)
                    mediaProjection = it
                }
            imageReader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                PixelFormat.RGBA_8888,
                2,
            )
            val display = virtualDisplay
            if (display == null) {
                virtualDisplay = projection.createVirtualDisplay(
                    "screenshot",
                    captureWidth,
                    captureHeight,
                    captureDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface,
                    null,
                    handler,
                ) ?: throw IllegalStateException("创建截屏虚拟显示失败")
            } else {
                display.resize(captureWidth, captureHeight, captureDpi)
                display.surface = imageReader.surface
            }
            activeImageReader = imageReader
            activeContinuation = continuation
            imageReader.setOnImageAvailableListener(
                { reader -> handleImageAvailable(reader, continuation) },
                handler,
            )
        } catch (e: Exception) {
            if (activeImageReader === imageReader) {
                releaseActiveCapture()
            } else {
                imageReader?.close()
            }
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun handleImageAvailable(
        reader: ImageReader,
        continuation: CancellableContinuation<Bitmap>,
    ) {
        if (activeContinuation !== continuation || !continuation.isActive) {
            if (activeContinuation === continuation) {
                releaseActiveCapture()
            }
            return
        }
        var image: Image? = null
        var bitmapWithStride: Bitmap? = null
        var bitmap: Bitmap? = null
        var result: Bitmap? = null
        var failure: Exception? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val plane = image.planes[0]
            val rowWidth = plane.rowStride / plane.pixelStride
            bitmapWithStride = createBitmap(rowWidth, image.height)
            bitmapWithStride.copyPixelsFromBuffer(plane.buffer)
            bitmap = Bitmap.createBitmap(
                bitmapWithStride,
                0,
                0,
                reader.width,
                reader.height,
            )
            if (bitmap === bitmapWithStride) {
                bitmapWithStride = null
            }
            if (bitmap.isFullTransparent()) {
                return
            }
            result = bitmap
            bitmap = null
        } catch (e: Exception) {
            failure = e
        } finally {
            bitmap?.recycle()
            bitmapWithStride?.recycle()
            image?.close()
        }
        releaseActiveCapture()
        result?.let { captured ->
            if (continuation.isActive) {
                continuation.resume(captured)
            } else {
                captured.recycle()
            }
        }
        failure?.let { error ->
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }
    }

    private fun releaseActiveCapture() {
        activeImageReader?.setOnImageAvailableListener(null, null)
        try {
            virtualDisplay?.surface = null
        } catch (e: Exception) {
            LogUtils.d("释放截屏 Surface 失败", e)
        }
        activeImageReader?.close()
        activeImageReader = null
        activeContinuation = null
    }
}
