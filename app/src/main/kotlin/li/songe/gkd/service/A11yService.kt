package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context.WINDOW_SERVICE
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.a11y.A11yCommonImpl
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.a11y.updateTopActivity
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.store.updateEnableAutomator
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.AutomatorModeOption
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.OnA11yLife
import li.songe.gkd.util.componentName
import li.songe.gkd.util.runMainPost
import li.songe.gkd.util.toast
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@SuppressLint("AccessibilityPolicy")
open class A11yService : AccessibilityService(), OnA11yLife, A11yCommonImpl {
    override val mode get() = AutomatorModeOption.A11yMode
    override val scope = useScope()
    override val windowNodeInfo: AccessibilityNodeInfo? get() = rootInActiveWindow
    override val windowInfos: List<AccessibilityWindowInfo> get() = windows
    override suspend fun screenshot(): Bitmap? = suspendCoroutine { continuation ->
        if (AndroidTarget.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                application.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onFailure(errorCode: Int) = continuation.resume(null)
                    override fun onSuccess(screenshot: ScreenshotResult) = try {
                        continuation.resume(
                            Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer, screenshot.colorSpace
                            )
                        )
                    } finally {
                        screenshot.hardwareBuffer.close()
                    }
                }
            )
        } else {
            continuation.resume(null)
        }
    }

    override val ruleEngine by lazy { A11yRuleEngine(this) }

    override fun onCreate() = onCreated()
    override fun onServiceConnected() = onA11yConnected()
    override fun onInterrupt() {}
    override fun onDestroy() = onDestroyed()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = ruleEngine.onA11yEvent(event)

    val startTime = System.currentTimeMillis()
    override var justStarted: Boolean = true
        get() {
            if (field) {
                field = System.currentTimeMillis() - startTime < 3_000
            }
            return field
        }

    private var tempShutdownFlag = false

    override fun shutdown(temp: Boolean) {
        if (temp) {
            tempShutdownFlag = true
        }
        disableSelf()
    }

    private var destroyed = false
    private var connected = false

    val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        onA11yConnected { instance = this }
        onDestroyed { instance = null }
        onCreated {
            if (currentAppUseA11y) {
                updateEnableAutomator(true)
            } else {
                toast("当前为自动化模式，无障碍将自动关闭", forced = true)
                runMainPost(1) { shutdown(true) }
            }
        }
        onDestroyed {
            if (tempShutdownFlag) {
                toast("无障碍局部关闭")
            } else {
                toast("无障碍已关闭")
                updateEnableAutomator(false)
            }
        }
        useAliveOverlayView()
        onCreated { StatusService.autoStart() }
        onDestroyed {
            shizukuContextFlow.value.topCpn()?.let { cpn ->
                // com.android.systemui
                if (!topActivityFlow.value.sameAs(cpn.packageName, cpn.className)) {
                    updateTopActivity(cpn.packageName, cpn.className)
                }
            }
        }
        onDestroyed { destroyed = true }
        onA11yConnected {
            connected = true
            toast("无障碍已启动")
            if (currentAppUseA11y) {
                ruleEngine.onA11yConnected()
            }
        }
        onCreated {
            runMainPost(3000) {
                if (!(destroyed || connected)) {
                    toast("无障碍启动超时，请尝试关闭重启", forced = true)
                }
            }
        }
    }

    companion object {
        val a11yCn by lazy { SelectToSpeakService::class.componentName }
        val isRunning = MutableStateFlow(false)

        @Volatile
        var instance: A11yService? = null
            private set
    }
}

private fun A11yService.useAliveOverlayView() {
    val context = this
    var aliveView: View? = null
    val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    fun removeA11View() {
        if (aliveView != null) {
            wm.removeView(aliveView)
            aliveView = null
        }
    }

    fun addA11View() {
        removeA11View()
        val tempView = View(context)
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags =
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = Gravity.START or Gravity.TOP
            width = 1
            height = 1
            packageName = context.packageName
        }
        try {
            // 某些设备 android.view.WindowManager$BadTokenException
            wm.addView(tempView, lp)
            aliveView = tempView
        } catch (e: Throwable) {
            aliveView = null
            LogUtils.d(e)
            toast("添加无障碍保活失败\n请尝试重启无障碍")
        }
    }
    onA11yConnected { addA11View() }
    onDestroyed { removeA11View() }
}
