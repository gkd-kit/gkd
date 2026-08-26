package li.songe.gkd.priv

import android.accessibilityservice.IAccessibilityServiceClient
import android.app.IUiAutomationConnection
import android.os.RemoteException
import priv.kit.core.Privilege


// https://diff.songe.li/i/UiAutomationConnection
class ProxyUiAutomationConnection(
    privilegeContext: PrivilegeContext,
) : IUiAutomationConnection.Stub() {
    companion object {
        private const val INITIAL_FROZEN_ROTATION_UNSPECIFIED = -1
    }

    private val mLock = Any()
    private val mToken = privilegeContext.serverLifecycleBinder
    private var mClient: IAccessibilityServiceClient? = null
    private var mInitialFrozenRotation = INITIAL_FROZEN_ROTATION_UNSPECIFIED
    private var mIsShutdown = false
    private var mOwningUid = 0
    private val serverInfo = privilegeContext.serverInfo
    private val wmManager = privilegeContext.wmManager
    private val a11yManager = privilegeContext.a11yManager

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
            mOwningUid = serverInfo.uid
            registerUiTestAutomationServiceLocked(client, currentUserId, flags)
            storeRotationStateLocked()
        }
    }


    override fun disconnect() {
        synchronized(mLock) {
            throwIfShutdownLocked()
            if (!isConnectedLocked()) {
                throw IllegalStateException("Already disconnected.")
            }
            try {
                if (Privilege.pingServer()) {
                    throwIfCalledByNotTrustedUidLocked()
                    unregisterUiTestAutomationServiceLocked()
                    restoreRotationStateLocked()
                }
            } finally {
                mOwningUid = -1
                mClient = null
            }
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

    private fun registerUiTestAutomationServiceLocked(
        client: IAccessibilityServiceClient,
        userId: Int,
        flags: Int,
    ) {
        val info = createUiAutomationServiceInfo()
        try {
            a11yManager.registerUiTestAutomationService(mToken, client, info, userId, flags)
            mClient = client
        } catch (re: RemoteException) {
            throw IllegalStateException(
                "Error while registering UiTestAutomationService for "
                        + "user " + userId + ".", re
            )
        }
    }

    private fun unregisterUiTestAutomationServiceLocked() {
        a11yManager.value.unregisterUiTestAutomationService(mClient)
    }

    private fun storeRotationStateLocked() {
        try {
            if (wmManager.value.isRotationFrozen()) {
                mInitialFrozenRotation = wmManager.value.getDefaultDisplayRotation()
            }
        } catch (_: RemoteException) {
        }
    }

    private fun restoreRotationStateLocked() {
        try {
            val caller = "UiAutomationConnection#restoreRotationStateLocked"
            if (mInitialFrozenRotation != INITIAL_FROZEN_ROTATION_UNSPECIFIED) {
                wmManager.freezeRotation(mInitialFrozenRotation, caller)
            } else {
                wmManager.thawRotation(caller)
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
        val callingUid = serverInfo.uid
        if (callingUid != mOwningUid && mOwningUid != android.os.Process.SYSTEM_UID && callingUid != 0) {
            throw SecurityException("Calling from not trusted UID!")
        }
    }
}
