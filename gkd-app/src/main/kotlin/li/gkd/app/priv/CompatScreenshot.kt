package li.songe.gkd.priv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.Surface
import android.view.SurfaceControlHidden
import android.window.ScreenCapture
import android.window.ScreenCaptureInternal
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import li.songe.gkd.util.AndroidTarget
import androidx.core.graphics.createBitmap

object CompatScreenshot {
    fun capture(
        context: Context,
        windowManager: IWindowManager,
        userService: IUserService,
    ): Bitmap? {
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(DEFAULT_DISPLAY) ?: return null
        val displaySize = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(displaySize)
        val crop = Rect(0, 0, displaySize.x, displaySize.y)
        return if (AndroidTarget.UPSIDE_DOWN_CAKE) {
            captureByWindowManager(windowManager, crop)
        } else {
            userService.takeScreenshot(crop, display.rotation)
        }?.apply {
            setHasAlpha(false)
        }
    }

    fun captureBySurfaceControl(crop: Rect, rotation: Int): Bitmap? {
        val width = crop.width()
        val height = crop.height()
        return when {
            AndroidTarget.S -> {
                val displayToken = SurfaceControlHidden.getInternalDisplayToken()
                val captureArgs = SurfaceControlHidden.DisplayCaptureArgs.Builder(displayToken)
                    .setSourceCrop(crop)
                    .setSize(width, height)
                    .build()
                SurfaceControlHidden.captureDisplay(captureArgs)?.asBitmap()
            }

            AndroidTarget.P -> {
                SurfaceControlHidden.screenshot(crop, width, height, rotation)
            }

            else -> captureOreo(crop, rotation)
        }
    }

    private fun captureOreo(crop: Rect, rotation: Int): Bitmap? {
        val displayWidth = crop.width()
        val displayHeight = crop.height()
        val rotated = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        val screenshotWidth = if (rotated) displayHeight else displayWidth
        val screenshotHeight = if (rotated) displayWidth else displayHeight
        var screenshot = SurfaceControlHidden.screenshot(screenshotWidth, screenshotHeight)
            ?: return null
        if (rotation != Surface.ROTATION_0) {
            val unrotatedScreenshot = createBitmap(displayWidth, displayHeight)
            Canvas(unrotatedScreenshot).apply {
                translate(unrotatedScreenshot.width / 2f, unrotatedScreenshot.height / 2f)
                rotate(degreesForRotation(rotation))
                translate(-screenshotWidth / 2f, -screenshotHeight / 2f)
                drawBitmap(screenshot, 0f, 0f, null)
                setBitmap(null)
            }
            screenshot.recycle()
            screenshot = unrotatedScreenshot
        }
        return screenshot
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun captureByWindowManager(
        windowManager: IWindowManager,
        crop: Rect,
    ): Bitmap? {
        return if (useScreenCaptureInternal) {
            captureByWindowManagerInternal(windowManager, crop)
        } else {
            val captureArgs = ScreenCapture.CaptureArgs.Builder()
                .setSourceCrop(crop)
                .build()
            val listener = ScreenCapture.createSyncCaptureListener()
            windowManager.captureDisplay(DEFAULT_DISPLAY, captureArgs, listener)
            listener.buffer?.let(::copyToSoftwareBitmap)
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun captureByWindowManagerInternal(
        windowManager: IWindowManager,
        crop: Rect,
    ): Bitmap? {
        val captureArgs = ScreenCaptureInternal.CaptureArgs.Builder()
            .setSourceCrop(crop)
            .build()
        val listener = ScreenCaptureInternal.createSyncCaptureListener()
        windowManager.captureDisplay(DEFAULT_DISPLAY, captureArgs, listener)
        return listener.buffer?.let(::copyToSoftwareBitmap)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun copyToSoftwareBitmap(
        buffer: ScreenCapture.ScreenshotHardwareBuffer,
    ): Bitmap? {
        val screenshot = buffer.asBitmap() ?: return null
        return try {
            buffer.hardwareBuffer.use {
                screenshot.copy(Bitmap.Config.ARGB_8888, false)
            }
        } finally {
            screenshot.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun copyToSoftwareBitmap(
        buffer: ScreenCaptureInternal.ScreenshotHardwareBuffer,
    ): Bitmap? {
        val screenshot = buffer.asBitmap() ?: return null
        return try {
            buffer.hardwareBuffer.use {
                screenshot.copy(Bitmap.Config.ARGB_8888, false)
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun degreesForRotation(rotation: Int): Float = when (rotation) {
        Surface.ROTATION_90 -> 270f
        Surface.ROTATION_180 -> 180f
        Surface.ROTATION_270 -> 90f
        else -> 0f
    }

    private val hasScreenCaptureInternal by lazy {
        HiddenApiDetect.detectHiddenClass(
            "android.window.ScreenCaptureInternal",
        )
    }

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
    private val useScreenCaptureInternal
        get() = AndroidTarget.BAKLAVA && hasScreenCaptureInternal
}
