package li.gkd.app.service


import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import li.gkd.app.text.UiStrings
import li.gkd.app.a11y.topActivityFlow
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.store.FileStateStore
import li.gkd.app.ui.icon.DragPan
import li.gkd.app.ui.style.AppTheme
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.util.BarUtils
import li.gkd.app.util.ScreenUtils
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import li.gkd.app.util.px
import li.gkd.app.util.runMainPost
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons

private var tempShareContext: ShareContext? = null
private fun acquireShareContext(): ShareContextLease {
    val shareContext = tempShareContext ?: ShareContext().apply { tempShareContext = this }
    shareContext.count++
    return ShareContextLease(shareContext)
}

private class ShareContextLease(val context: ShareContext) : AutoCloseable {
    override fun close() {
        context.count--
        if (context.count == 0) {
            context.scope.cancel()
            tempShareContext = null
        }
    }
}

private class ShareContext {
    var count = 0
    var overlayContentHidden by mutableStateOf(false)
    val scope = MainScope()
    val positionMapFlow = FileStateStore.createJsonFlow<Map<String, List<Int>>>(
        key = "overlay_position",
        default = { emptyMap() },
        scope = scope,
    )

    init {
        scope.launch {
            var canDrawOverlays = PermissionStates.drawOverlays.updateAndGet()
            topActivityFlow
                .map { it.appId to it.activityId }
                .distinctUntilChanged()
                .collectLatest {
                    var i = 0
                    while (i < 6 && isActive) {
                        val oldV = canDrawOverlays
                        val newV = PermissionStates.drawOverlays.updateAndGet()
                        canDrawOverlays = newV
                        if (!newV && oldV) {
                            toast(UiStrings.overlay_screen_denied)
                            break
                        }
                        delay(500.milliseconds)
                        i++
                    }
                }
        }
    }
}

abstract class OverlayWindowService(
    private val positionKey: String,
) : LifecycleHookService(), SavedStateRegistryOwner {
    companion object {
        private var aliveSize = 0
        val isAnyAlive: Boolean
            get() = aliveSize > 0
    }

    private val resizeFlow = MutableSharedFlow<Unit>()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lifecycleScope.launch { resizeFlow.emit(Unit) }
    }

    private val registryController = SavedStateRegistryController.create(this).apply {
        performAttach()
        performRestore(null)
    }
    override val savedStateRegistry = registryController.savedStateRegistry

    @Composable
    abstract fun ComposeContent()

    @Composable
    fun ClosableTitle(
        title: String,
        onMinimizeRequest: (() -> Unit)? = null,
        minimizeContentDescription: String = UiStrings.window_minimize,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            GkIcon(imageVector = DragPan, modifier = Modifier.iconTextSize())
            Text(text = title, modifier = Modifier.weight(1f))
            if (onMinimizeRequest != null) {
                GkIcon(
                    imageVector = GkIcons.CollapseContent,
                    contentDescription = minimizeContentDescription,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = throttle(onMinimizeRequest))
                        .iconTextSize(),
                )
            }
            GkIcon(
                imageVector = GkIcons.Close,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = throttle {
                        stopSelf()
                    })
                    .iconTextSize()
            )
        }
    }

    open fun onClickView() {}

    open fun isViewClickEnabled(): Boolean = true

    open fun onLongClickView() {}

    val view by lazy {
        ComposeView(this).apply {
            setViewTreeSavedStateRegistryOwner(this@OverlayWindowService)
            setViewTreeLifecycleOwner(this@OverlayWindowService)
            setContent {
                AppTheme(invertedTheme = true) {
                    Box(
                        modifier = Modifier.drawWithContent {
                            if (!shareContext.overlayContentHidden) drawContent()
                        },
                    ) {
                        ComposeContent()
                    }
                }
            }
        }
    }

    protected val isOverlayContentHidden
        get() = shareContext.overlayContentHidden

    protected suspend fun <T> withAllOverlaysHidden(block: suspend () -> T): T {
        shareContext.overlayContentHidden = true
        return try {
            awaitOverlayContentHidden()
            block()
        } finally {
            shareContext.overlayContentHidden = false
        }
    }

    private suspend fun awaitOverlayContentHidden() =
        suspendCancellableCoroutine { continuation ->
            val afterFrame = Runnable {
                if (continuation.isActive) continuation.resume(Unit)
            }
            val onFrame = Runnable {
                if (continuation.isActive) view.post(afterFrame)
            }
            continuation.invokeOnCancellation {
                view.removeCallbacks(onFrame)
                view.removeCallbacks(afterFrame)
            }
            view.postOnAnimation(onFrame)
        }

    private val minMargin get() = 10.dp.px.toInt()
    private val defaultPosition get() = listOf(minMargin, BarUtils.getStatusBarHeight())

    private lateinit var shareContext: ShareContext

    private val positionFlow by lazy {
        MutableStateFlow(
            shareContext.positionMapFlow.value[positionKey].let {
                if (it != null && it.size >= 2) {
                    it
                } else {
                    defaultPosition
                }
            }
        )
    }

    init {
        useStopServiceReceiver()
        onCreated {
            val shareContextLease = acquireShareContext()
            shareContext = shareContextLease.context
            onDestroyed { shareContextLease.close() }
            aliveSize++
            onDestroyed { runMainPost(1000) { aliveSize-- } }
            lifecycleScope.launch {
                positionFlow.drop(1).debounce(300.milliseconds).collect { pos ->
                    shareContext.positionMapFlow.update {
                        it.toMutableMap().apply {
                            set(positionKey, pos)
                        }
                    }
                }
            }
            attachView()
            onDestroyed { app.windowManager.removeView(view) }
        }
    }

    private fun attachView() {
        val marginX = minMargin
        val marginY = minMargin
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            windowAnimations = android.R.style.Animation_Dialog
            gravity = Gravity.START or Gravity.TOP
            x = positionFlow.value.first()
            y = positionFlow.value.last()
        }
        var screenWidth = ScreenUtils.getScreenWidth()
        var screenHeight = ScreenUtils.getScreenHeight()
        var paramsXy = layoutParams.x to layoutParams.y
        var fixMoveFlag = 0
        val fixLimitXy = {
            screenWidth = ScreenUtils.getScreenWidth()
            screenHeight = ScreenUtils.getScreenHeight()
            val x = layoutParams.x.coerceIn(marginX, screenWidth - view.width - marginX)
            val y = layoutParams.y.coerceIn(
                marginY,
                screenHeight - view.height - marginY
            )
            if (x != layoutParams.x || y != layoutParams.y) {
                positionFlow.value = listOf(x, y)
                val startX = layoutParams.x
                val startY = layoutParams.y
                fixMoveFlag++
                val tempFlag = fixMoveFlag
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 300
                    addUpdateListener { animator ->
                        if (tempFlag == fixMoveFlag) {
                            val fraction = animator.animatedValue as Float
                            layoutParams.x = (startX + (x - startX) * fraction).toInt()
                            layoutParams.y = (startY + (y - startY) * fraction).toInt()
                            app.windowManager.updateViewLayout(view, layoutParams)
                        } else {
                            pause()
                        }
                    }
                    doOnEnd {
                        if (tempFlag == fixMoveFlag) {
                            fixMoveFlag = 0
                        }
                    }
                }.start()
            }
        }
        lifecycleScope.launch {
            view.viewTreeObserver.addOnGlobalLayoutListener { launch { resizeFlow.emit(Unit) } }
            resizeFlow.debounce(100.milliseconds).collect { fixLimitXy() }
        }
        var downXy: Pair<Float, Float>? = null
        var longClickJob: kotlinx.coroutines.Job? = null
        var viewClickEnabled = false
        var isDragging = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        @SuppressLint("ClickableViewAccessibility")
        view.setOnTouchListener { _, event ->
            if (fixMoveFlag > 0) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downXy = event.rawX to event.rawY
                    screenWidth = ScreenUtils.getScreenWidth()
                    screenHeight = ScreenUtils.getScreenHeight()
                    paramsXy = layoutParams.x to layoutParams.y
                    viewClickEnabled = isViewClickEnabled()
                    isDragging = false
                    longClickJob = null
                    longClickJob = lifecycleScope.launch {
                        delay(500.milliseconds)
                        longClickJob = null
                        if (downXy != null) {
                            onLongClickView()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    downXy?.let { downEvent ->
                        val dx = (event.rawX - downEvent.first).toInt()
                        val dy = (event.rawY - downEvent.second).toInt()
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            isDragging = true
                        }
                        val x = dx + paramsXy.first
                        val y = dy + paramsXy.second
                        layoutParams.x = x.coerceIn(marginX, screenWidth - view.width - marginX)
                        layoutParams.y = y.coerceIn(
                            marginY,
                            screenHeight - view.height - marginY
                        )
                        positionFlow.value = listOf(layoutParams.x, layoutParams.y)
                        app.windowManager.updateViewLayout(view, layoutParams)
                        if (isDragging) {
                            longClickJob?.cancel()
                            longClickJob = null
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val gapTime = event.eventTime - event.downTime
                    if (viewClickEnabled && !isDragging && gapTime <= ViewConfiguration.getTapTimeout()) {
                        onClickView()
                    }
                    downXy = null
                    longClickJob?.cancel()
                    longClickJob = null
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    downXy = null
                    longClickJob?.cancel()
                    longClickJob = null
                    true
                }

                else -> false
            }
        }
        app.windowManager.addView(view, layoutParams)
    }

}
