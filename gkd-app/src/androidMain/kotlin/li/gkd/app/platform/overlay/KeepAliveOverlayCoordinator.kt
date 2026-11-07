package li.gkd.app.platform.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.permission.PermissionStates
import li.gkd.app.resources.*
import li.gkd.app.service.A11yService
import li.gkd.app.service.CompanionServiceController
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.util.ToastUtils

object KeepAliveOverlayCoordinator {
    enum class Source {
        Application,
        Accessibility,
        Companion,
    }

    private val applicationOverlay = MutableStateFlow<AttachedOverlay?>(null)
    private val accessibilityOverlay = MutableStateFlow<AttachedOverlay?>(null)
    private val companionOverlay = MutableStateFlow<AttachedOverlay?>(null)

    fun initialize() {
        appScope.launch {
            try {
                combine(
                        SettingsRepository.settings
                            .map { it.enableStatusOverlay }
                            .distinctUntilChanged(),
                        PermissionStates.drawOverlays.stateFlow,
                        combine(
                            A11yService.isRunning,
                            accessibilityOverlay,
                            CompanionServiceController.instanceFlow,
                            companionOverlay,
                        ) { a11yRunning, a11yOverlay, companion, companionOverlay ->
                            (a11yRunning && a11yOverlay != null) ||
                                (companion != null && companionOverlay != null)
                        },
                    ) { enabled, granted, a11yAttached ->
                        Pair(enabled && granted, a11yAttached)
                    }
                    .distinctUntilChanged()
                    .collectLatest { (enabled, a11yAttached) ->
                        when {
                            !enabled -> release(Source.Application, app)
                            a11yAttached -> releaseAfterHandoff(Source.Application, app)
                            else ->
                                acquire(
                                        source = Source.Application,
                                        owner = app,
                                        context = app,
                                        windowType =
                                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                    )
                                    .onFailure(LogUtils::d)
                        }
                    }
            } finally {
                release(Source.Application, app)
            }
        }
    }

    suspend fun acquireAccessibility(source: Source, service: AccessibilityService) {
        val connectedAt = SystemClock.elapsedRealtime()
        val retryDelays = longArrayOf(250, 750, 1500)
        for (attempt in 0..retryDelays.size) {
            if (attempt > 0) delay(retryDelays[attempt - 1])
            val result =
                acquire(
                    source = source,
                    owner = service,
                    context = service,
                    windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                )
            val elapsed = SystemClock.elapsedRealtime() - connectedAt
            if (result.isSuccess) {
                LogUtils.i(
                    "$source keep-alive overlay attached: attempt=${attempt + 1}, elapsed=${elapsed}ms",
                )
                return
            }
            val retry =
                result.exceptionOrNull() is WindowManager.BadTokenException &&
                    attempt < retryDelays.size
            LogUtils.w(
                "$source keep-alive overlay failed: attempt=${attempt + 1}, elapsed=${elapsed}ms, retry=$retry",
                result.exceptionOrNull(),
            )
            if (!retry) break
        }
        ToastUtils.show(Res.string.a11y_keep_alive_failed.getSync())
    }

    fun releaseAccessibilityAfterHandoff(source: Source, owner: Any) {
        appScope.launch {
            val replacement =
                when {
                    source != Source.Accessibility &&
                        A11yService.isRunning.value &&
                        accessibilityOverlay.value != null -> Source.Accessibility
                    source != Source.Companion &&
                        CompanionServiceController.instanceFlow.value != null &&
                        companionOverlay.value != null -> Source.Companion
                    SettingsRepository.settings.value.enableStatusOverlay &&
                        PermissionStates.drawOverlays.value -> Source.Application
                    else -> null
                }
            releaseAfterHandoff(source, owner, replacement)
        }
    }

    @Synchronized
    private fun acquire(
        source: Source,
        owner: Any,
        context: Context,
        windowType: Int,
    ): Result<Unit> {
        val state = overlayState(source)
        val current = state.value
        if (current?.owner === owner) return Result.success(Unit)
        val overlay = runCatching {
            KeepAliveOverlay(context, windowType)
        }
            .getOrElse {
                return Result.failure(it)
            }
        current?.overlay?.close()
        state.value = AttachedOverlay(owner, overlay)
        return Result.success(Unit)
    }

    private suspend fun releaseAfterHandoff(
        source: Source,
        owner: Any,
        replacement: Source? = null,
    ) {
        if (replacement != null) {
            withTimeoutOrNull(HANDOFF_DELAY_MILLIS) {
                overlayState(replacement).first { it != null }
            }
        }
        delay(HANDOFF_DELAY_MILLIS)
        release(source, owner)
    }

    @Synchronized
    private fun release(source: Source, owner: Any) {
        val state = overlayState(source)
        val current = state.value
        if (current?.owner !== owner) return
        state.value = null
        current.overlay.close()
    }

    private fun overlayState(source: Source): MutableStateFlow<AttachedOverlay?> =
        when (source) {
            Source.Application -> applicationOverlay
            Source.Accessibility -> accessibilityOverlay
            Source.Companion -> companionOverlay
        }

    private data class AttachedOverlay(
        val owner: Any,
        val overlay: KeepAliveOverlay,
    )

    private class KeepAliveOverlay(
        context: Context,
        windowType: Int,
    ) : AutoCloseable {
        private val windowManager = context.getSystemService(WindowManager::class.java)
        private val view = View(context)
        private var attached = false

        init {
            val layoutParams =
                WindowManager.LayoutParams().apply {
                    type = windowType
                    alpha = 0f
                    format = PixelFormat.TRANSLUCENT
                    flags =
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    gravity = Gravity.START or Gravity.TOP
                    width = 1
                    height = 1
                    packageName = context.packageName
                }
            windowManager.addView(view, layoutParams)
            attached = true
        }

        @Synchronized
        override fun close() {
            if (!attached) return
            attached = false
            runCatching { windowManager.removeView(view) }.onFailure(LogUtils::d)
        }
    }

    private const val HANDOFF_DELAY_MILLIS = 1_000L
}
