package li.songe.gkd.service

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.notif.StopServiceReceiver
import li.songe.gkd.notif.trackNotif
import li.songe.gkd.priv.toHidden
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.DefaultSimpleLifeImpl
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass
import kotlin.math.min
import kotlin.math.pow

class TrackService : LifecycleService(), SavedStateRegistryOwner,
    OnSimpleLife by DefaultSimpleLifeImpl() {
    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    val registryController = SavedStateRegistryController.create(this).apply {
        performAttach()
        performRestore(null)
    }
    override val savedStateRegistry = registryController.savedStateRegistry
    override val scope get() = lifecycleScope

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val resizeFlow = MutableSharedFlow<Unit>()
    override fun onConfigurationChanged(newConfig: Configuration) {
        lifecycleScope.launch { resizeFlow.emit(Unit) }
    }

    val strokeWidth = 2f
    val pointSize = ScreenUtils.getScreenSize().let { min(it.width, it.height) } * 0.1f
    val pointRadius = pointSize / 2

    private fun DrawScope.drawTrackPoint(center: Offset) {
        drawLine(
            color = Color.Yellow,
            start = Offset(center.x, center.y - pointRadius),
            end = Offset(center.x, center.y + pointRadius),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = Color.Yellow,
            start = Offset(center.x - pointRadius, center.y),
            end = Offset(center.x + pointRadius, center.y),
            strokeWidth = strokeWidth,
        )
        val ringSize = 3
        repeat(ringSize) { i ->
            drawCircle(
                color = Color.Red,
                radius = pointRadius * 0.8f / ringSize * (i + 1),
                center = center,
                style = Stroke(strokeWidth)
            )
        }
    }

    private abstract inner class FloatLayer {
        private val view = ComposeView(this@TrackService).apply {
            setViewTreeSavedStateRegistryOwner(this@TrackService)
            setViewTreeLifecycleOwner(this@TrackService)
            setContent(::ComposeContent)
        }
        private val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            if (AndroidTarget.S) {
                alpha = app.inputManager.maximumObscuringOpacityForTouch
            }
        }
        private val subScope = MainScope()
        private val boundsRect = Rect()
        protected var connected = false
        protected var removed = false

        @Composable
        abstract fun ComposeContent()
        abstract fun syncRotation()

        fun removeView() {
            subScope.cancel()
            windowManager.removeView(view)
            removed = true
        }

        fun getRect(): Rect? {
            if (!connected || removed) return null
            return boundsRect
        }

        fun setAlpha(alpha: Float) {
            if (!connected || removed) return
            if (layoutParams.alpha == alpha) return
            layoutParams.alpha = alpha
            windowManager.updateViewLayout(view, layoutParams)
        }

        fun updateViewLayout(
            x: Number,
            y: Number,
            width: Number = layoutParams.width,
            height: Number = layoutParams.height,
        ) {
            layoutParams.x = x.toInt()
            layoutParams.y = y.toInt()
            layoutParams.width = width.toInt()
            layoutParams.height = height.toInt()
            boundsRect.set(
                layoutParams.x,
                layoutParams.y,
                layoutParams.x + layoutParams.width,
                layoutParams.y + layoutParams.height,
            )
            if (!connected) {
                connected = true
                windowManager.addView(view, layoutParams)
                subScope.launch { resizeFlow.collect { syncRotation() } }
            } else {
                windowManager.updateViewLayout(view, layoutParams)
                recalcOverlappingAlpha()
            }
        }
    }

    private inner class PointFloatLayer(val point: TrackPoint) : FloatLayer() {
        @Composable
        override fun ComposeContent() = Canvas(modifier = Modifier.fillMaxSize()) {
            drawTrackPoint(Offset(pointRadius, pointRadius))
        }

        override fun syncRotation() {
            val (x, y) = point.getCurCenter() - Offset(pointRadius, pointRadius)
            updateViewLayout(x, y)
        }

        init {
            updateViewLayout(point.x - pointRadius, point.y - pointRadius, pointSize, pointSize)
        }
    }

    private inner class SwipePointFloatLayer(val swipePoint: SwipeTrackPoint) : FloatLayer() {
        @Composable
        override fun ComposeContent() = Canvas(modifier = Modifier.fillMaxSize()) {
            val sc = swipePoint.start.getCurCenter()
            val ec = swipePoint.end.getCurCenter()
            val start = Offset(
                if (sc.x <= ec.x) pointRadius else size.width - pointRadius,
                if (sc.y <= ec.y) pointRadius else size.height - pointRadius
            )
            val end = Offset(
                if (sc.x <= ec.x) size.width - pointRadius else pointRadius,
                if (sc.y <= ec.y) size.height - pointRadius else pointRadius
            )
            drawTrackPoint(start)
            drawTrackPoint(end)
            drawLine(
                color = Color.Blue,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
            )
        }

        override fun syncRotation() {
            val f = animator.animatedValue as Float
            val sc = swipePoint.start.getCurCenter()
            val ec = swipePoint.end.getCurCenter()
            val cur = Offset(sc.x + (ec.x - sc.x) * f, sc.y + (ec.y - sc.y) * f)
            updateViewLayout(
                minOf(sc.x, cur.x) - pointRadius,
                minOf(sc.y, cur.y) - pointRadius,
                maxOf(sc.x, cur.x) - minOf(sc.x, cur.x) + pointSize,
                maxOf(sc.y, cur.y) - minOf(sc.y, cur.y) + pointSize
            )
        }

        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = swipePoint.duration
            addUpdateListener {
                if (removed) {
                    cancel()
                } else {
                    val f = it.animatedValue as Float
                    val sc = swipePoint.start.getCurCenter()
                    val ec = swipePoint.end.getCurCenter()
                    val cur = Offset(sc.x + (ec.x - sc.x) * f, sc.y + (ec.y - sc.y) * f)
                    updateViewLayout(
                        minOf(sc.x, cur.x) - pointRadius,
                        minOf(sc.y, cur.y) - pointRadius,
                        maxOf(sc.x, cur.x) - minOf(sc.x, cur.x) + pointSize,
                        maxOf(sc.y, cur.y) - minOf(sc.y, cur.y) + pointSize
                    )
                }
            }
        }

        init {
            val sc = swipePoint.start.getCurCenter()
            updateViewLayout(sc.x - pointRadius, sc.y - pointRadius, pointSize, pointSize)
            animator.start()
        }
    }

    private val layerMap = hashMapOf<Int, FloatLayer>().apply {
        onDestroyed {
            forEach { it.value.removeView() }
            clear()
        }
    }

    private fun recalcOverlappingAlpha() {
        if (!AndroidTarget.S) return
        val maxOpacity = app.inputManager.maximumObscuringOpacityForTouch
        val entries = layerMap.values.mapNotNull { layer ->
            layer.getRect()?.let { rect -> layer to rect }
        }
        for ((layer, rect) in entries) {
            var overlapCount = 1
            for ((other, otherRect) in entries) {
                if (other !== layer && Rect.intersects(rect, otherRect)) {
                    overlapCount++
                }
            }
            val safeAlpha = if (overlapCount > 1) {
                1f - (1f - maxOpacity).toDouble().pow(1.0 / overlapCount).toFloat()
            } else {
                maxOpacity
            }
            layer.setAlpha(safeAlpha)
        }
    }

    val tapDelay = 100L
    val missDelay = 7500L

    private fun addPoint(point: TrackPoint) {
        runScopePost(tapDelay) {
            layerMap[point.id] = PointFloatLayer(point)
            recalcOverlappingAlpha()
        }
        runScopePost(missDelay) {
            layerMap.remove(point.id)?.removeView()
            recalcOverlappingAlpha()
        }
    }

    private fun addSwipePoint(swipePoint: SwipeTrackPoint) {
        runScopePost(tapDelay) {
            layerMap[swipePoint.id] = SwipePointFloatLayer(swipePoint)
            recalcOverlappingAlpha()
        }
        runScopePost(missDelay + swipePoint.duration) {
            layerMap.remove(swipePoint.id)?.removeView()
            recalcOverlappingAlpha()
        }
    }

    init {
        useLogLifecycle()
        onCreated { service = this }
        onDestroyed { service = null }
        useAliveFlow(isRunning)
        useAliveToast("轨迹提示")
        StopServiceReceiver.autoRegister()
        onCreated { trackNotif.notifyService() }
    }

    companion object {
        @Volatile
        private var service: TrackService? = null
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        fun start() = startForegroundServiceByClass(TrackService::class)
        fun stop() = stopServiceByClass(TrackService::class)
        fun addA11yNodePosition(node: AccessibilityNodeInfo) {
            service?.addPoint(
                TrackPoint(
                    node.toHidden.boundsInScreen.centerX().toFloat(),
                    node.toHidden.boundsInScreen.centerY().toFloat(),
                )
            )
        }

        fun addXyPosition(x: Float, y: Float) {
            service?.addPoint(TrackPoint(x, y))
        }

        fun addSwipePosition(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            duration: Long
        ) {
            service?.addSwipePoint(
                SwipeTrackPoint(
                    TrackPoint(startX, startY),
                    TrackPoint(endX, endY),
                    duration
                )
            )
        }
    }
}

private val autoIncreaseId = atomic(0)

private data class TrackPoint(
    val x: Float,
    val y: Float,
) {
    val id = autoIncreaseId.incrementAndGet()
    val screenSize = ScreenUtils.getScreenSize()
    val rotation = app.compatDisplay.rotation

    fun getCurCenter(): Offset {
        val curSize = ScreenUtils.getScreenSize()
        val curRotation = app.compatDisplay.rotation
        val (physX, physY) = screenToPhysical(x, y, screenSize.width, screenSize.height, rotation)
        return physicalToScreen(physX, physY, curSize.width, curSize.height, curRotation)
    }

    private fun screenToPhysical(
        sx: Float, sy: Float,
        sw: Int, sh: Int,
        rot: Int,
    ): Offset = when (rot) {
        Surface.ROTATION_0 -> Offset(sx, sy)
        Surface.ROTATION_90 -> Offset(sh - sy, sx)
        Surface.ROTATION_180 -> Offset(sw - sx, sh - sy)
        Surface.ROTATION_270 -> Offset(sy, sw - sx)
        else -> Offset(sx, sy)
    }

    private fun physicalToScreen(
        px: Float, py: Float,
        sw: Int, sh: Int,
        rot: Int,
    ): Offset = when (rot) {
        Surface.ROTATION_0 -> Offset(px, py)
        Surface.ROTATION_90 -> Offset(py, sh - px)
        Surface.ROTATION_180 -> Offset(sw - px, sh - py)
        Surface.ROTATION_270 -> Offset(sw - py, px)
        else -> Offset(px, py)
    }
}

private data class SwipeTrackPoint(
    val start: TrackPoint,
    val end: TrackPoint,
    val duration: Long,
) {
    val id = autoIncreaseId.incrementAndGet()
}
