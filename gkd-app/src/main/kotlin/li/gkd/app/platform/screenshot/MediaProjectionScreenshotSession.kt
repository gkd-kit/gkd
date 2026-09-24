package li.gkd.app.platform.screenshot

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
import li.gkd.app.text.UiStrings
import li.gkd.app.app
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ScreenUtils
import li.gkd.app.util.isFullTransparent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// https://github.com/npes87184/ScreenShareTile/blob/master/app/src/main/java/com/npes87184/screenshottile/ScreenshotService.kt

class MediaProjectionScreenshotSession(
    private val screenshotIntent: Intent,
    private val onInvalidated: (MediaProjectionScreenshotSession) -> Unit,
) : AutoCloseable {
    private enum class TerminationReason(
        val message: String,
        val stopProjection: Boolean,
        val notifyOwner: Boolean,
    ) {
        ProjectionStopped(UiStrings.screenshot_permission_expired, stopProjection = false, notifyOwner = true),
        Closed(UiStrings.screenshot_service_stopped, stopProjection = true, notifyOwner = false),
        InitializationFailed(UiStrings.screenshot_service_init_failed, stopProjection = true, notifyOwner = true),
    }

    private val handlerThread = HandlerThread("gkd-screenshot").apply { start() }
    private val handler = Handler(handlerThread.looper)

    @Volatile
    private var closed = false
    private var terminatedOnHandler = false
    private var projectionCreationAttempted = false
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var activeImageReader: ImageReader? = null
    private var activeContinuation: CancellableContinuation<Bitmap>? = null

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            terminate(TerminationReason.ProjectionStopped)
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
        if (!handler.post { terminate(TerminationReason.Closed) }) {
            handlerThread.quitSafely()
        }
    }

    private fun terminate(
        reason: TerminationReason,
        failure: Exception = IllegalStateException(reason.message),
    ) {
        if (terminatedOnHandler) return
        terminatedOnHandler = true
        closed = true
        val continuation = activeContinuation
        releaseActiveCapture()
        virtualDisplay?.release()
        virtualDisplay = null
        val projection = mediaProjection
        mediaProjection = null
        if (projection != null) {
            try {
                projection.unregisterCallback(mediaProjectionCallback)
            } catch (e: Exception) {
                LogUtils.d("Failed to unregister screenshot authorization callback", e)
            }
            if (reason.stopProjection) {
                try {
                    projection.stop()
                } catch (e: Exception) {
                    LogUtils.d("Failed to stop screenshot authorization", e)
                }
            }
        }
        if (continuation?.isActive == true) {
            continuation.resumeWithException(failure)
        }
        if (reason.notifyOwner) {
            try {
                onInvalidated(this)
            } catch (e: Exception) {
                LogUtils.d("Failed to invalidate screenshot notification session", e)
            }
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
                IllegalStateException(UiStrings.screenshot_thread_unavailable)
            )
        }
    }

    private fun startCapture(continuation: CancellableContinuation<Bitmap>) {
        if (!continuation.isActive) return
        if (closed) {
            continuation.resumeWithException(
                IllegalStateException(UiStrings.screenshot_service_unavailable)
            )
            return
        }
        if (activeContinuation != null) {
            continuation.resumeWithException(
                IllegalStateException(UiStrings.screenshot_capturing)
            )
            return
        }

        activeContinuation = continuation
        var imageReader: ImageReader? = null
        try {
            val captureWidth = width
            val captureHeight = height
            val captureDpi = dpi
            val projection = getOrCreateProjection()
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
                ) ?: throw IllegalStateException(UiStrings.screenshot_virtual_display_failed)
            } else {
                display.resize(captureWidth, captureHeight, captureDpi)
                display.surface = imageReader.surface
            }
            activeImageReader = imageReader
            imageReader.setOnImageAvailableListener(
                { reader -> handleImageAvailable(reader, continuation) },
                handler,
            )
        } catch (e: Exception) {
            if (activeImageReader !== imageReader) {
                imageReader?.close()
            }
            terminate(TerminationReason.InitializationFailed, e)
        }
    }

    private fun getOrCreateProjection(): MediaProjection {
        mediaProjection?.let { return it }
        check(!projectionCreationAttempted) { UiStrings.screenshot_permission_reuse_forbidden }
        projectionCreationAttempted = true
        return (
            app.mediaProjectionManager.getMediaProjection(
                RESULT_OK,
                screenshotIntent,
            ) ?: throw IllegalStateException(UiStrings.screenshot_permission_failed)
            ).also {
            it.registerCallback(mediaProjectionCallback, handler)
            mediaProjection = it
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
            LogUtils.d("Failed to release screenshot Surface", e)
        }
        activeImageReader?.close()
        activeImageReader = null
        activeContinuation = null
    }
}
