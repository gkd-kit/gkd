package li.gkd.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.accessibility.selecttospeak.SelectToSpeakService
import com.google.android.accessibility.selecttospeak.SelectToSpeakServicePrivileged
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
import li.gkd.app.priv.hasInputInjectionPermission
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.record.RuntimeRecordRepository
import li.gkd.app.resources.*
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.settings.SettingsRepository
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (connected) matchHost.onA11yEvent(event)
    }

    private var tempShutdownFlag = false
    @Volatile private var replacementRequested = false

    fun shutdownForReplacement() {
        if (replacementRequested) return
        replacementRequested = true
        shutdown(true)
    }

    fun writeReplacement(write: () -> Boolean): Boolean {
        val previous = replacementRequested
        replacementRequested = true
        var written = false
        try {
            written = write()
            return written
        } finally {
            if (!written) replacementRequested = previous
        }
    }

    override fun shutdown(temp: Boolean) {
        keepAliveOverlayJob?.cancel()
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
            scope.launch {
                delay(3000)
                if (!(destroyed || connected)) {
                    ToastUtils.show(Res.string.a11y_start_timeout.getSync(), forced = true)
                }
            }
        }
        lifecycleHooks.onDestroyed {
            destroyed = true
            scope.cancel()
            if (!connected) return@onDestroyed
            MatchingRuntime.coordinator.detach(this)
            val ownsState = instance === this
            if (ownsState) {
                instanceFlow.value = null
                isRunning.value = false
            }
            CompanionServiceController.update()
            releaseKeepAliveOverlayAfterHandoff()
            if (!replacementRequested) {
                if (tempShutdownFlag) {
                    ToastUtils.show(Res.string.a11y_partially_disabled.getSync())
                } else {
                    ToastUtils.show(Res.string.a11y_stopped.getSync())
                    if (ownsState) updateEnableAutomator(false)
                }
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

    private var keepAliveOverlayJob: Job? = null

    private fun attachKeepAliveOverlay() {
        keepAliveOverlayJob?.cancel()
        val connectedAt = SystemClock.elapsedRealtime()
        keepAliveOverlayJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val retryDelays = longArrayOf(250, 750, 1500)
                for (attempt in 0..retryDelays.size) {
                    if (attempt > 0) delay(retryDelays[attempt - 1])
                    val result =
                        KeepAliveOverlayCoordinator.acquire(
                            source = KeepAliveOverlayCoordinator.Source.Accessibility,
                            owner = this@A11yService,
                            context = this@A11yService,
                            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        )
                    val elapsed = SystemClock.elapsedRealtime() - connectedAt
                    if (result.isSuccess) {
                        LogUtils.i(
                            "A11y keep-alive overlay attached: attempt=${attempt + 1}, elapsed=${elapsed}ms",
                        )
                        return@launch
                    }
                    val retry =
                        result.exceptionOrNull() is WindowManager.BadTokenException &&
                            attempt < retryDelays.size
                    LogUtils.w(
                        "A11y keep-alive overlay failed: attempt=${attempt + 1}, elapsed=${elapsed}ms, retry=$retry",
                        result.exceptionOrNull(),
                    )
                    if (!retry) break
                }
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
        val useA11y = SettingsRepository.settings.value.useA11y
        if (!useA11y || this::class.componentName != preferredComponent) {
            if (!useA11y) {
                ToastUtils.show(Res.string.a11y_automation_mode_notice.getSync(), forced = true)
            }
            shutdownForReplacement()
            scope.cancel()
            return
        }
        instance?.takeIf { it !== this }?.shutdownForReplacement()
        connected = true
        instanceFlow.value = this
        isRunning.value = true
        updateEnableAutomator(true)
        StatusService.autoStart()
        attachKeepAliveOverlay()
        ToastUtils.show(Res.string.a11y_started.getSync())
        AndroidAutomationSessions.onA11yConnected(this)
        CompanionServiceController.update()
    }

    override fun onDestroy() {
        lifecycleHooks.dispatchDestroyed()
        super.onDestroy()
        if (connected) {
            AndroidAutomationSessions.recordServiceEvent(this, MatchServiceEvent.Disconnected)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        keepAliveOverlayJob?.cancel()
        return super.onUnbind(intent)
    }

    companion object {
        val standardComponent by lazy { SelectToSpeakService::class.componentName }
        val privilegedComponent by lazy { SelectToSpeakServicePrivileged::class.componentName }
        val preferredComponent
            get() =
                if (privilegeContextFlow.value != null && hasInputInjectionPermission())
                    privilegedComponent
                else standardComponent

        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        val instanceFlow: StateFlow<A11yService?>
            field = MutableStateFlow(null)

        val instance: A11yService?
            get() = instanceFlow.value
    }
}
