package li.songe.gkd.service

import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.Surface
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.util.lerp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.notif.StopServiceReceiver
import li.songe.gkd.notif.trackNotif
import li.songe.gkd.shizuku.casted
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.runMainPost
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass

class TrackService : LifecycleService(), SavedStateRegistryOwner, OnSimpleLife {
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

    val curRotationFlow = MutableStateFlow(app.compatDisplay.rotation).apply {
        lifecycleScope.launch {
            resizeFlow.collect { update { app.compatDisplay.rotation } }
        }
    }
    val strokeWidth = 2f
    val lineMargin = 75f

    private fun DrawScope.drawTrackPoint(center: Offset) {

        drawLine(
            color = Color.Yellow,
            start = Offset(center.x, center.y - lineMargin),
            end = Offset(center.x, center.y + lineMargin),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = Color.Yellow,
            start = Offset(center.x - lineMargin, center.y),
            end = Offset(center.x + lineMargin, center.y),
            strokeWidth = strokeWidth,
        )
        drawCircle(
            color = Color.Red,
            radius = 10f,
            center = center,
        )
        drawCircle(
            color = Color.Red,
            radius = 20f,
            center = center,
            style = Stroke(4f)
        )
        drawCircle(
            color = Color.Red,
            radius = 30f,
            center = center,
            style = Stroke(4f)
        )
    }

    @Composable
    private fun SwipePointCanvas(swipePoint: SwipeTrackPoint, curRotation: Int) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(null) {
            // 匀速直线
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(swipePoint.duration.toInt(), easing = LinearEasing),
            )
            delayRemovePosition(swipePoint.id)
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startCenter = swipePoint.start.getCenter(size, curRotation)
            drawTrackPoint(startCenter)
            val endCenter = swipePoint.end.getCenter(size, curRotation)
            val midCenter = Offset(
                lerp(startCenter.x, endCenter.x, progress.value),
                lerp(startCenter.y, endCenter.y, progress.value)
            )
            drawTrackPoint(midCenter)
            drawLine(
                color = Color.Blue,
                start = startCenter,
                end = midCenter,
                strokeWidth = strokeWidth,
            )
        }
    }

    @Composable
    fun ComposeContent() {
        val curRotation by curRotationFlow.collectAsState()
        val positionList = pointListFlow.collectAsState().value
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            positionList.forEach { point ->
                drawTrackPoint(point.getCenter(size, curRotation))
            }
        }
        val swipePointList = swipePointListFlow.collectAsState().value
        swipePointList.forEach { swipePoint ->
            key(swipePoint.id) { SwipePointCanvas(swipePoint, curRotation) }
        }
    }

    val view by lazy {
        ComposeView(this).apply {
            setViewTreeSavedStateRegistryOwner(this@TrackService)
            setViewTreeLifecycleOwner(this@TrackService)
            setContent {
                Box(modifier = Modifier.fillMaxSize()) {
                    ComposeContent()
                }
            }
        }
    }

    val layoutParams = WindowManager.LayoutParams(
        ScreenUtils.getScreenWidth(),
        ScreenUtils.getScreenHeight(),
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
            // fix #1325
            alpha = app.inputManager.maximumObscuringOpacityForTouch
        }
    }

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        useAliveToast("轨迹提示")
        StopServiceReceiver.autoRegister()
        onCreated { trackNotif.notifyService() }
        onCreated { windowManager.addView(view, layoutParams) }
        onDestroyed { windowManager.removeView(view) }
        onDestroyed { clearPosition() }
        onCreated {
            scope.launch {
                resizeFlow.collect {
                    layoutParams.width = ScreenUtils.getScreenWidth()
                    layoutParams.height = ScreenUtils.getScreenHeight()
                    windowManager.updateViewLayout(view, layoutParams)
                }
            }
        }
    }

    companion object {
        private val pointListFlow = MutableStateFlow<List<TrackPoint>>(emptyList())
        private val swipePointListFlow = MutableStateFlow<List<SwipeTrackPoint>>(emptyList())
        private fun clearPosition() {
            pointListFlow.value = emptyList()
            swipePointListFlow.value = emptyList()
            autoIncreaseId.value = 0
        }

        private fun delayRemovePosition(id: Int) {
            runMainPost(7500) {
                pointListFlow.update { it.filter { v -> v.id != id } }
                swipePointListFlow.update { it.filter { v -> v.id != id } }
            }
        }

        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        fun start() = startForegroundServiceByClass(TrackService::class)
        fun stop() = stopServiceByClass(TrackService::class)
        fun addA11yNodePosition(node: AccessibilityNodeInfo) {
            if (!isRunning.value) return
            addXyPosition(
                node.casted.boundsInScreen.centerX().toFloat(),
                node.casted.boundsInScreen.centerY().toFloat(),
            )
        }

        fun addXyPosition(x: Float, y: Float) {
            if (!isRunning.value) return
            val p = TrackPoint(x, y)
            pointListFlow.update { it + p }
            delayRemovePosition(p.id)
        }

        fun addSwipePosition(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            duration: Long
        ) {
            if (!isRunning.value) return
            val p = SwipeTrackPoint(
                start = TrackPoint(startX, startY),
                end = TrackPoint(endX, endY),
                duration = duration,
            )
            swipePointListFlow.update { it + p }
        }
    }
}

private val autoIncreaseId = atomic(0)

private data class TrackPoint(
    val x: Float,
    val y: Float,
) {
    val id = autoIncreaseId.incrementAndGet()
    val screenWidth = ScreenUtils.getScreenWidth().toFloat()
    val screenHeight = ScreenUtils.getScreenHeight().toFloat()
    val rotation = app.compatDisplay.rotation

    fun getCenter(size: Size, curRotation: Int): Offset {
        val curWidth = size.width
        val curHeight = size.height
        val (physX, physY) = screenToPhysical(x, y, screenWidth, screenHeight, rotation)
        return physicalToScreen(physX, physY, curWidth, curHeight, curRotation)
    }

    private fun screenToPhysical(
        sx: Float, sy: Float,
        sw: Float, sh: Float,
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
        sw: Float, sh: Float,
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
