package li.gkd.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import li.gkd.app.appScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.AndroidAutomationSession
import li.gkd.app.matching.AndroidAutomationSessions
import li.gkd.app.matching.AndroidMatchHost
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchServiceEvent
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.input.currentTopActivity
import li.gkd.app.matching.input.updateTopActivity
import li.gkd.app.platform.lifecycle.LifecycleHooks
import li.gkd.app.platform.overlay.KeepAliveOverlayCoordinator
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.record.RuntimeRecordRepository
import li.gkd.app.resources.*
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.store.AppStore.updateEnableAutomator
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.componentName

@SuppressLint("AccessibilityPolicy")
abstract class A11yService : AccessibilityService(), AndroidAutomationSession {
    private val lifecycleHooks = LifecycleHooks()
    override val scope = MainScope()
    override val mode
        get() = AutomatorMode.A11y

    override val windowNodeInfo: AccessibilityNodeInfo?
        get() = rootInActiveWindow

    override val windowInfos: List<AccessibilityWindowInfo>
        get() = windows

    override suspend fun screenshot(): Bitmap? = suspendCancellableCoroutine { cont ->
        if (AndroidTarget.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                application.mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) {
                            cont.resume(null)
                        }
                    }

                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            if (cont.isActive) {
                                cont.resume(
                                    Bitmap.wrapHardwareBuffer(
                                        screenshot.hardwareBuffer,
                                        screenshot.colorSpace,
                                    ),
                                )
                            }
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }
                    }
                },
            )
        } else {
            cont.resume(null)
        }
    }

    override val matchHost by lazy { AndroidMatchHost(this) }
    override val subscribedEventTypes: Int?
        get() = serviceInfo?.eventTypes

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = matchHost.onA11yEvent(event)

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
        lifecycleHooks.useLogLifecycle(this)
        lifecycleHooks.onCreated {
            isRunning.value = true
            if (currentAppUseA11y) {
                updateEnableAutomator(true)
            } else {
                ToastUtils.show(Res.string.a11y_automation_mode_notice.getSync(), forced = true)
                scope.launch {
                    delay(1)
                    shutdown(true)
                }
            }
            StatusService.autoStart()
            scope.launch {
                delay(3000)
                if (!(destroyed || connected)) {
                    ToastUtils.show(Res.string.a11y_start_timeout.getSync(), forced = true)
                }
            }
        }
        lifecycleHooks.onDestroyed {
            MatchingRuntime.coordinator.detach(this)
            scope.cancel()
            if (instance === this) {
                instance = null
            }
            isRunning.value = false
            releaseKeepAliveOverlayAfterHandoff()
            if (tempShutdownFlag) {
                ToastUtils.show(Res.string.a11y_partially_disabled.getSync())
            } else {
                ToastUtils.show(Res.string.a11y_stopped.getSync())
                updateEnableAutomator(false)
            }
            val context = privilegeContextFlow.value
            val page = AndroidForegroundState.currentPage
            val cpn = context?.topCpn()
            AndroidForegroundState.withTopActivityLock {
                if (
                    cpn != null &&
                        context === privilegeContextFlow.value &&
                        page === AndroidForegroundState.currentPage &&
                        !currentTopActivity.sameAs(cpn.packageName, cpn.className)
                ) {
                    updateTopActivity(cpn.packageName, cpn.className)
                }
            }
            destroyed = true
            appScope.launch {
                try {
                    scope.coroutineContext[Job]?.join()
                    RuntimeRecordRepository.flush()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtils.e("Runtime record flush failed", e)
                }
            }
        }
    }

    private fun attachKeepAliveOverlay() {
        if (
            !KeepAliveOverlayCoordinator.acquire(
                source = KeepAliveOverlayCoordinator.Source.Accessibility,
                owner = this,
                context = this,
                windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            )
        ) {
            ToastUtils.show(Res.string.a11y_keep_alive_failed.getSync())
        }
    }

    private fun releaseKeepAliveOverlayAfterHandoff() {
        appScope.launch {
            KeepAliveOverlayCoordinator.releaseAfterHandoff(
                source = KeepAliveOverlayCoordinator.Source.Accessibility,
                owner = this@A11yService,
                replacement =
                    KeepAliveOverlayCoordinator.Source.Status.takeIf {
                        StatusService.isRunning.value
                    },
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleHooks.dispatchCreated()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LogUtils.i("onA11yConnected -> ${this::class.simpleName}")
        instance = this
        attachKeepAliveOverlay()
        connected = true
        ToastUtils.show(Res.string.a11y_started.getSync())
        if (currentAppUseA11y) {
            AndroidAutomationSessions.onA11yConnected(this)
        }
    }

    override fun onDestroy() {
        lifecycleHooks.dispatchDestroyed()
        super.onDestroy()
        AndroidAutomationSessions.recordServiceEvent(this, MatchServiceEvent.Disconnected)
    }

    companion object {
        val a11yCn by lazy { SelectToSpeakService::class.componentName }
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        @Volatile
        var instance: A11yService? = null
            private set
    }
}
