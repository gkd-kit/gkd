package li.songe.gkd.service


import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.lifecycle.LifecycleService
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
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.store.createAnyFlow
import li.songe.gkd.ui.style.AppTheme
import li.songe.gkd.util.BarUtils
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.mapState
import li.songe.gkd.util.px
import li.songe.gkd.util.toast

private var tempShareContext: ShareContext? = null
private fun OverlayWindowService.useShareContext(): ShareContext {
    val shareContext = tempShareContext ?: ShareContext().apply { tempShareContext = this }
    shareContext.count++
    onDestroyed {
        shareContext.count--
        if (shareContext.count == 0) {
            shareContext.scope.cancel()
            tempShareContext = null
        }
    }
    return shareContext
}

private class ShareContext {
    var count = 0
    val scope = MainScope()
    val positionMapFlow = createAnyFlow<Map<String, List<Int>>>(
        key = "overlay_position",
        default = { emptyMap() },
        scope = scope,
    )

    init {
        scope.launch {
            var canDrawOverlays = canDrawOverlaysState.updateAndGet()
            topActivityFlow
                .mapState(scope) { it.appId to it.activityId }
                .collectLatest {
                    var i = 0
                    while (i < 6 && isActive) {
                        val oldV = canDrawOverlays
                        val newV = canDrawOverlaysState.updateAndGet()
                        canDrawOverlays = newV
                        if (!newV && oldV) {
                            toast("当前界面拒绝显示悬浮窗")
                            break
                        }
                        delay(500)
                        i++
                    }
                }
        }
    }
}

abstract class OverlayWindowService(
    val positionKey: String,
) : LifecycleService(), SavedStateRegistryOwner, OnSimpleLife {
    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

    override val scope get() = lifecycleScope

    private val resizeFlow = MutableSharedFlow<Unit>()

    override fun onConfigurationChanged(newConfig: Configuration) {
        lifecycleScope.launch { resizeFlow.emit(Unit) }
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

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    @Composable
    abstract fun ComposeContent()

    open fun onClickView() {}

    val view by lazy {
        ComposeView(this).apply {
            setViewTreeSavedStateRegistryOwner(this@OverlayWindowService)
            setViewTreeLifecycleOwner(this@OverlayWindowService)
            setContent {
                AppTheme(invertedTheme = true) {
                    ComposeContent()
                }
            }
        }
    }

    private val minMargin get() = 10.dp.px.toInt()
    private val defaultPosition get() = listOf(minMargin, BarUtils.getStatusBarHeight())

    private val shareContext = useShareContext()

    private val positionFlow = MutableStateFlow(
        shareContext.positionMapFlow.value[positionKey].let {
            if (it != null && it.size >= 2) {
                it
            } else {
                defaultPosition
            }
        }
    )

    init {
        lifecycleScope.launch {
            positionFlow.drop(1).debounce(300).collect { pos ->
                shareContext.positionMapFlow.update {
                    it.toMutableMap().apply {
                        set(positionKey, pos)
                    }
                }
            }
        }
    }

    init {
        onCreated {
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
                    val newX = x
                    val newY = y
                    fixMoveFlag++
                    val tempFlag = fixMoveFlag
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 300
                        addUpdateListener { animator ->
                            if (tempFlag == fixMoveFlag) {
                                val fraction = animator.animatedValue as Float
                                layoutParams.x = (startX + (newX - startX) * fraction).toInt()
                                layoutParams.y = (startY + (newY - startY) * fraction).toInt()
                                windowManager.updateViewLayout(view, layoutParams)
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
                resizeFlow.debounce(100).collect { fixLimitXy() }
            }
            var downXy: Pair<Float, Float>? = null
            @SuppressLint("ClickableViewAccessibility")
            view.setOnTouchListener { _, event ->
                if (fixMoveFlag > 0) return@setOnTouchListener true
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downXy = event.rawX to event.rawY
                        screenWidth = ScreenUtils.getScreenWidth()
                        screenHeight = ScreenUtils.getScreenHeight()
                        paramsXy = layoutParams.x to layoutParams.y
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        downXy?.let { downEvent ->
                            val x = (event.rawX - downEvent.first).toInt() + paramsXy.first
                            val y = (event.rawY - downEvent.second).toInt() + paramsXy.second
                            layoutParams.x = x.coerceIn(marginX, screenWidth - view.width - marginX)
                            layoutParams.y = y.coerceIn(
                                marginY,
                                screenHeight - view.height - marginY
                            )
                            positionFlow.value = listOf(layoutParams.x, layoutParams.y)
                            windowManager.updateViewLayout(view, layoutParams)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val gapTime = event.eventTime - event.downTime
                        if (gapTime <= ViewConfiguration.getTapTimeout()) {
                            onClickView()
                        }
                        downXy = null
                        true
                    }

                    else -> false
                }
            }
            windowManager.addView(view, layoutParams)
        }
        onDestroyed { windowManager.removeView(view) }
    }
}