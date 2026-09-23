package li.gkd.app.priv

import android.annotation.SuppressLint
import android.app.UiAutomation
import android.app.UiAutomationHidden
import android.graphics.Bitmap
import android.os.HandlerThread
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.a11y.A11yCommonImpl
import li.gkd.app.a11y.A11yRuleEngine
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.store.AppStore.updateEnableAutomator
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.AutomatorModeOption
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils.toast

class AutomationService private constructor(
    private val privilegeContext: PrivilegeContext,
) : A11yCommonImpl {
    override val mode get() = AutomatorModeOption.AutomationMode
    private val handlerThread = HandlerThread("UiAutomatorHandlerThread")
    private val uiAutomationDelegate = lazy {
        UiAutomationHidden(
            handlerThread.looper,
            ProxyUiAutomationConnection(privilegeContext),
        ).toPublic
    }
    private val uiAutomation by uiAutomationDelegate

    override val scope = MainScope()

    override val ruleEngine by lazy { A11yRuleEngine(this) }

    private val listener = UiAutomation.OnAccessibilityEventListener {
        ruleEngine.onA11yEvent(it)
    }

    override suspend fun screenshot(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            privilegeContextFlow.value?.screenshot()
        } catch (e: Throwable) {
            LogUtils.d("takeScreenshot failed", e)
            null
        }
    }

    override val windowNodeInfo: AccessibilityNodeInfo? get() = uiAutomation.rootInActiveWindow
    override val windowInfos: List<AccessibilityWindowInfo> get() = uiAutomation.windows
    private val startTime = System.currentTimeMillis()
    override var justStarted: Boolean = true
        get() {
            if (field) {
                field = System.currentTimeMillis() - startTime < 3_000
            }
            return field
        }

    private var connected = false

    // https://github.com/android-cs/16/blob/main/cmds/uiautomator/library/testrunner-src/com/android/uiautomator/core/UiAutomationShellWrapper.java#L25
    private fun connect() {
        handlerThread.start()
        uiAutomation.toHidden.connect(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiAutomation.setOnAccessibilityEventListener(listener)
        connected = true
        toast(UiStrings.automation_started)
        updateEnableAutomator(true)
        A11yRuntime.onA11yConnected(this)
    }

    private fun disconnect() {
        scope.cancel()
        handlerThread.quit()
        if (!uiAutomationDelegate.isInitialized()) return
        val wasConnected = connected
        try {
            if (wasConnected) {
                uiAutomation.setOnAccessibilityEventListener(null)
            }
            uiAutomation.toHidden.disconnect()
        } catch (e: Exception) {
            LogUtils.d("disconnect automation failed", e)
        } finally {
            uiAutomation.quitRemoteCallbackThread()
            connected = false
            if (wasConnected) {
                if (tempShutdownFlag) {
                    toast(UiStrings.automation_partially_disabled)
                } else {
                    toast(UiStrings.automation_stopped)
                    updateEnableAutomator(false)
                }
            }
        }
    }

    private var tempShutdownFlag = false
    private val shutdown = atomic(false)
    override fun shutdown(temp: Boolean) {
        if (!shutdown.compareAndSet(expect = false, update = true)) return
        if (temp) {
            tempShutdownFlag = true
        }
        try {
            disconnect()
        } finally {
            uiAutomationFlow.compareAndSet(this, null)
        }
    }

    companion object {
        private val connectLock = Any()

        fun isOtherUiAutomationRunning(): Boolean {
            if (uiAutomationFlow.value != null) return false
            return privilegeContextFlow.value?.isUiAutomationRunning() == true
        }

        fun showOccupiedWarning(silent: Boolean = false) {
            toast(UiStrings.automation_service_occupied)
            if (!silent) {
                uiAutomationOccupiedFlow.value = true
            }
        }

        fun dismissOccupiedWarning() {
            uiAutomationOccupiedFlow.value = false
        }

        fun tryConnect(silent: Boolean = false) {
            synchronized(connectLock) {
                uiAutomationOccupiedFlow.value = false
                if (uiAutomationFlow.value?.connected == true) {
                    return@synchronized
                }
                uiAutomationFlow.value?.shutdown()
                val privilegeContext = privilegeContextFlow.value ?: return@synchronized
                try {
                    if (isOtherUiAutomationRunning()) {
                        showOccupiedWarning(silent)
                        return@synchronized
                    }
                } catch (e: Exception) {
                    toast(UiStrings.automation_state_check_failed(e.message))
                    LogUtils.d("detect automation state failed", e)
                    return@synchronized
                }
                val instance = AutomationService(privilegeContext)
                try {
                    instance.connect()
                    if (!uiAutomationFlow.compareAndSet(expect = null, update = instance)) {
                        instance.shutdown(true)
                        return@synchronized
                    }
                    if (
                        privilegeContextFlow.value !== privilegeContext ||
                        !privilegeContext.serverLifecycleBinder.pingBinder()
                    ) {
                        instance.shutdown(true)
                    }
                } catch (e: Exception) {
                    instance.shutdown(true)
                    toast(UiStrings.automation_start_failed(e.message))
                    LogUtils.d(e)
                }
            }
        }
    }
}

val uiAutomationFlow: StateFlow<AutomationService?>
    field = MutableStateFlow(null)
val uiAutomationOccupiedFlow: StateFlow<Boolean>
    field = MutableStateFlow(false)

private val remoteCallbackThreadField by lazy {
    if (AndroidTarget.P) {
        // UiAutomation 在 CONNECTING 阶段同步失败时，disconnect() 会在进入自身的
        // finally 前直接抛错，因此需要通过内部字段兜底关闭其回调线程。
        @SuppressLint("SoonBlockedPrivateApi")
        UiAutomation::class.java.getDeclaredField("mRemoteCallbackThread").apply {
            isAccessible = true
        }
    } else {
        null
    }
}

private fun UiAutomation.quitRemoteCallbackThread() {
    try {
        remoteCallbackThreadField?.let { field ->
            (field.get(this) as? HandlerThread)?.quit()
            field.set(this, null)
        }
    } catch (e: Exception) {
        LogUtils.d("quit UiAutomation callback thread failed", e)
    }
}
