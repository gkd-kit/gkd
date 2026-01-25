package li.songe.gkd.shizuku

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfoHidden
import android.accessibilityservice.IAccessibilityServiceClient
import android.app.IUiAutomationConnection
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Binder
import android.os.Build
import android.os.RemoteException
import android.view.Display.DEFAULT_DISPLAY
import android.view.accessibility.AccessibilityEvent
import android.window.ScreenCapture
import androidx.annotation.RequiresApi
import li.songe.gkd.util.AndroidTarget
import rikka.shizuku.Shizuku


// https://diff.songe.li/?ref=UiAutomationConnection
class ProxyUiAutomationConnection : IUiAutomationConnection.Stub() {
    companion object {
        private const val INITIAL_FROZEN_ROTATION_UNSPECIFIED = -1
    }

    private val mLock = Any()
    private val mToken = Binder()
    private var mClient: IAccessibilityServiceClient? = null
    private var mInitialFrozenRotation = INITIAL_FROZEN_ROTATION_UNSPECIFIED
    private var mIsShutdown = false
    private var mOwningUid = 0
    private val mWindowManager
        get() = shizukuContextFlow.value.wmManager?.value ?: throw ShizukuOffException()
    private val manager
        get() = shizukuContextFlow.value.a11yManager?.value ?: throw ShizukuOffException()

    override fun connect(
        client: IAccessibilityServiceClient?,
        flags: Int,
    ) {
        if (client == null) {
            throw IllegalArgumentException("Client cannot be null!")
        }
        synchronized(mLock) {
            throwIfShutdownLocked()
            if (isConnectedLocked()) {
                throw IllegalStateException("Already connected.")
            }
            mOwningUid = Shizuku.getUid() // Binder.getCallingUid()
            registerUiTestAutomationServiceLocked(client, currentUserId, flags)
            storeRotationStateLocked()
        }
    }


    override fun disconnect() {
        synchronized(mLock) {
            throwIfCalledByNotTrustedUidLocked()
            throwIfShutdownLocked()
            if (!isConnectedLocked()) {
                throw IllegalStateException("Already disconnected.")
            }
            mOwningUid = -1
            unregisterUiTestAutomationServiceLocked()
            restoreRotationStateLocked()
        }
    }

    override fun shutdown() {
        synchronized(mLock) {
            if (isConnectedLocked()) {
                throwIfCalledByNotTrustedUidLocked()
            }
            throwIfShutdownLocked()
            mIsShutdown = true
            if (isConnectedLocked()) {
                disconnect()
            }
        }
    }

    // https://diff.songe.li/?ref=UiAutomationConnection.takeScreenshot
    override fun takeScreenshot(width: Int, height: Int): Bitmap? {
        synchronized(mLock) {
            throwIfCalledByNotTrustedUidLocked()
            throwIfShutdownLocked()
            throwIfNotConnectedLocked()
        }
        val identity = clearCallingIdentity()
        try {
            return shizukuContextFlow.value.serviceWrapper?.run {
                userService.takeScreenshot1(width, height)
            }
        } finally {
            restoreCallingIdentity(identity)
        }
    }

    override fun takeScreenshot(
        crop: Rect,
        rotation: Int,
    ): Bitmap? {
        synchronized(mLock) {
            throwIfCalledByNotTrustedUidLocked()
            throwIfShutdownLocked()
            throwIfNotConnectedLocked()
        }
        val identity = clearCallingIdentity()
        try {
            return shizukuContextFlow.value.serviceWrapper?.run {
                userService.takeScreenshot2(crop, rotation)
            }
        } finally {
            restoreCallingIdentity(identity)
        }
    }

    override fun takeScreenshot(crop: Rect): Bitmap? {
        synchronized(mLock) {
            throwIfCalledByNotTrustedUidLocked()
            throwIfShutdownLocked()
            throwIfNotConnectedLocked()
        }
        val identity = clearCallingIdentity()
        try {
            if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                val captureArgs = ScreenCapture.CaptureArgs.Builder()
                    .setSourceCrop(crop)
                    .build()
                val syncScreenCapture = ScreenCapture.createSyncCaptureListener()
                mWindowManager.captureDisplay(DEFAULT_DISPLAY, captureArgs, syncScreenCapture)
                val screenshotBuffer = syncScreenCapture.buffer
                return screenshotBuffer?.asBitmap()
            } else {
                return shizukuContextFlow.value.serviceWrapper?.run {
                    userService.takeScreenshot3(crop)
                }
            }
        } catch (re: RemoteException) {
            re.rethrowAsRuntimeException()
        } finally {
            restoreCallingIdentity(identity)
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun takeScreenshot(
        crop: Rect,
        listener: ScreenCapture.ScreenCaptureListener,
    ): Boolean {
        synchronized(mLock) {
            throwIfCalledByNotTrustedUidLocked()
            throwIfShutdownLocked()
            throwIfNotConnectedLocked()
        }
        val identity = clearCallingIdentity()
        try {
            val captureArgs = ScreenCapture.CaptureArgs.Builder()
                .setSourceCrop(crop)
                .build()
            mWindowManager.captureDisplay(DEFAULT_DISPLAY, captureArgs, listener)
        } catch (re: RemoteException) {
            re.rethrowAsRuntimeException()
        } finally {
            restoreCallingIdentity(identity)
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun takeScreenshot(
        crop: Rect,
        listener: ScreenCapture.ScreenCaptureListener,
        displayId: Int,
    ): Boolean {
        synchronized(mLock) {
            throwIfCalledByNotTrustedUidLocked()
            throwIfShutdownLocked()
            throwIfNotConnectedLocked()
        }
        val identity = clearCallingIdentity()
        try {
            val captureArgs = ScreenCapture.CaptureArgs.Builder()
                .setSourceCrop(crop)
                .build()
            mWindowManager.captureDisplay(displayId, captureArgs, listener)
        } catch (re: RemoteException) {
            re.rethrowAsRuntimeException()
        } finally {
            restoreCallingIdentity(identity)
        }
        return true
    }

    private fun registerUiTestAutomationServiceLocked(
        client: IAccessibilityServiceClient,
        userId: Int,
        flags: Int,
    ) {
        // see app/src/main/res/xml/ab_desc.xml
        val info = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            this.flags = (this.flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfoHidden.FLAG_FORCE_DIRECT_BOOT_AWARE)
        }
        info.casted.apply {
            setCapabilities(AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT)
            if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                setAccessibilityTool(true)
            }
        }
        try {
            if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                manager.registerUiTestAutomationService(mToken, client, info, userId, flags)
            } else {
                manager.registerUiTestAutomationService(mToken, client, info, flags)
            }
            mClient = client
        } catch (re: RemoteException) {
            throw IllegalStateException(
                "Error while registering UiTestAutomationService for "
                        + "user " + userId + ".", re
            )
        }
    }

    private fun unregisterUiTestAutomationServiceLocked() {
        manager.unregisterUiTestAutomationService(mClient)
        mClient = null
    }

    private fun storeRotationStateLocked() {
        try {
            if (mWindowManager.isRotationFrozen()) {
                mInitialFrozenRotation = mWindowManager.getDefaultDisplayRotation()
            }
        } catch (_: RemoteException) {
        }
    }

    private fun restoreRotationStateLocked() {
        try {
            if (mInitialFrozenRotation != INITIAL_FROZEN_ROTATION_UNSPECIFIED) {
                if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                    mWindowManager.freezeRotation(
                        mInitialFrozenRotation,
                        "UiAutomationConnection#restoreRotationStateLocked"
                    )
                } else {
                    mWindowManager.freezeRotation(mInitialFrozenRotation)
                }
            } else {
                if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                    mWindowManager.thawRotation("UiAutomationConnection#restoreRotationStateLocked")
                } else {
                    mWindowManager.thawRotation()
                }
            }
        } catch (_: RemoteException) {
        }
    }

    private fun throwIfShutdownLocked() {
        if (mIsShutdown) {
            throw IllegalStateException("Connection shutdown!")
        }
    }

    private fun isConnectedLocked(): Boolean = mClient != null

    private fun throwIfCalledByNotTrustedUidLocked() {
        val callingUid = Shizuku.getUid()
        if (callingUid != mOwningUid && mOwningUid != android.os.Process.SYSTEM_UID && callingUid != 0) {
            throw SecurityException("Calling from not trusted UID!")
        }
    }

    private fun throwIfNotConnectedLocked() {
        if (!isConnectedLocked()) {
            throw IllegalStateException("Not connected!")
        }
    }
}
