package li.gkd.app.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.util.LogUtils

object KeepAliveOverlayCoordinator {
    enum class Source {
        Status,
        Accessibility,
    }

    val statusAttached: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val accessibilityAttached: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private var statusOverlay: AttachedOverlay? = null
    private var accessibilityOverlay: AttachedOverlay? = null

    @Synchronized
    fun acquire(
        source: Source,
        owner: Any,
        context: Context,
        windowType: Int,
    ): Boolean {
        val current = get(source)
        if (current?.owner === owner) return true
        val overlay = runCatching { KeepAliveOverlay(context, windowType) }
            .onFailure(LogUtils::d)
            .getOrNull() ?: return false
        current?.overlay?.close()
        set(source, AttachedOverlay(owner, overlay))
        attachedState(source).value = true
        return true
    }

    suspend fun releaseAfterHandoff(
        source: Source,
        owner: Any,
        replacement: Source? = null,
    ) {
        if (replacement != null) {
            withTimeoutOrNull(HANDOFF_DELAY_MILLIS) {
                attachedState(replacement).first { it }
            }
        }
        delay(HANDOFF_DELAY_MILLIS)
        release(source, owner)
    }

    @Synchronized
    fun release(source: Source, owner: Any) {
        val current = get(source)
        if (current?.owner !== owner) return
        set(source, null)
        attachedState(source).value = false
        current.overlay.close()
    }

    private fun get(source: Source): AttachedOverlay? = when (source) {
        Source.Status -> statusOverlay
        Source.Accessibility -> accessibilityOverlay
    }

    private fun set(source: Source, value: AttachedOverlay?) {
        when (source) {
            Source.Status -> statusOverlay = value
            Source.Accessibility -> accessibilityOverlay = value
        }
    }

    private fun attachedState(source: Source): MutableStateFlow<Boolean> = when (source) {
        Source.Status -> statusAttached
        Source.Accessibility -> accessibilityAttached
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
            val layoutParams = WindowManager.LayoutParams().apply {
                type = windowType
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
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
            runCatching { windowManager.removeView(view) }
                .onFailure(LogUtils::d)
        }
    }

    private const val HANDOFF_DELAY_MILLIS = 1_000L
}
