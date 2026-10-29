package li.gkd.app.priv

import android.accessibilityservice.IAccessibilityServiceClient
import android.app.IUiAutomationConnection
import android.os.Process
import android.os.RemoteException
import priv.kit.core.Privilege

// https://diff.songe.li/i/UiAutomationConnection
class ProxyUiAutomationConnection(
    private val privilegeContext: PrivilegeContext,
) : IUiAutomationConnection.Stub() {
    private val mLock = Any()
    private var mClient: IAccessibilityServiceClient? = null
    private var mIsShutdown = false
    private var mOwningUid = 0
    private val serverInfo
        get() = privilegeContext.serverInfo

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
            privilegeContext.registerUiTestAutomationService(
                privilegeContext.serverLifecycleBinder,
                client,
                info,
                userId,
                flags,
            )
            mClient = client
        } catch (re: RemoteException) {
            throw IllegalStateException(
                "Error while registering UiTestAutomationService for " + "user " + userId + ".",
                re,
            )
        }
    }

    private fun unregisterUiTestAutomationServiceLocked() {
        privilegeContext.unregisterUiTestAutomationService(requireNotNull(mClient))
    }

    private fun throwIfShutdownLocked() {
        if (mIsShutdown) {
            throw IllegalStateException("Connection shutdown!")
        }
    }

    private fun isConnectedLocked(): Boolean = mClient != null

    private fun throwIfCalledByNotTrustedUidLocked() {
        val callingUid = serverInfo.uid
        if (callingUid != mOwningUid && mOwningUid != Process.SYSTEM_UID && callingUid != 0) {
            throw SecurityException("Calling from not trusted UID!")
        }
    }
}
