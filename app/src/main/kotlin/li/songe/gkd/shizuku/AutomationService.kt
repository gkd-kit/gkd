package li.songe.gkd.shizuku

import android.app.UiAutomation
import android.app.UiAutomationHidden
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.HandlerThread
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import li.songe.gkd.a11y.A11yCommonImpl
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.store.updateEnableAutomator
import li.songe.gkd.util.AutomatorModeOption
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.createGkdTempDir
import li.songe.gkd.util.toast

class AutomationService private constructor() : A11yCommonImpl {
    override val mode get() = AutomatorModeOption.AutomationMode
    private val handlerThread = HandlerThread("UiAutomatorHandlerThread")
    private val uiAutomation by lazy {
        UiAutomationHidden(
            handlerThread.looper,
            ProxyUiAutomationConnection(),
        ).castedHidden
    }

    override val scope = MainScope()

    override val ruleEngine by lazy { A11yRuleEngine(this) }

    private val listener = UiAutomation.OnAccessibilityEventListener {
        ruleEngine.onA11yEvent(it)
    }

    override suspend fun screenshot(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            uiAutomation.takeScreenshot()
        } catch (e: Throwable) {
            LogUtils.d("takeScreenshot failed, rollback to screencapFile", e)
            val tempDir = createGkdTempDir()
            val fp = tempDir.resolve("screenshot.png")
            val ok = shizukuContextFlow.value.serviceWrapper?.screencapFile(fp.absolutePath)
            if (ok == true && fp.exists()) {
                BitmapFactory.decodeFile(fp.absolutePath).apply {
                    tempDir.deleteRecursively()
                }
            } else {
                null
            }
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
        uiAutomation.casted.connect(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiAutomation.setOnAccessibilityEventListener(listener)
        connected = true
        toast("自动化已启动")
        updateEnableAutomator(true)
        ruleEngine.onA11yConnected()
    }

    private fun disconnect() {
        scope.cancel()
        handlerThread.quit()
        if (!connected) return
        uiAutomation.setOnAccessibilityEventListener(null)
        safeInvokeShizuku {
            uiAutomation.casted.disconnect()
        }
        if (tempShutdownFlag) {
            toast("自动化局部关闭")
        } else {
            toast("自动化已关闭")
            updateEnableAutomator(false)
        }
    }

    private var tempShutdownFlag = false
    override fun shutdown(temp: Boolean) {
        if (temp) {
            tempShutdownFlag = true
        }
        disconnect()
        uiAutomationFlow.value = null
    }

    companion object {
        private val loading = atomic(false)
        fun tryConnect(silent: Boolean = false) {
            if (loading.value) return
            loading.value = true
            try {
                automationRegisteredExceptionFlow.value = null
                if (uiAutomationFlow.value?.connected == true) {
                    return
                }
                uiAutomationFlow.value?.shutdown()
                val instance = AutomationService()
                try {
                    instance.connect()
                    uiAutomationFlow.value = instance
                } catch (e: Exception) {
                    instance.disconnect()
                    uiAutomationFlow.value = null
                    // https://github.com/android-cs/16/blob/main/services/accessibility/java/com/android/server/accessibility/UiAutomationManager.java#L110
                    if (e is IllegalStateException && e.message?.contains("already registered") == true) {
                        toast("自动化启动失败，被其他应用占用")
                        if (!silent) {
                            automationRegisteredExceptionFlow.value = e
                        }
                        LogUtils.d(e.message)
                    } else {
                        toast("自动化启动失败：${e.message}")
                        LogUtils.d(e)
                    }
                }
            } finally {
                loading.value = false
            }
        }
    }
}

val uiAutomationFlow = MutableStateFlow<AutomationService?>(null)
val automationRegisteredExceptionFlow = MutableStateFlow<Exception?>(null)
