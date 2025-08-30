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
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.store.createTextFlow
import li.songe.gkd.util.OnSimpleLife
import li.songe.gkd.util.mapState
import li.songe.gkd.util.px
import li.songe.gkd.util.toast

private var instanceFlags = mutableListOf<Long>()

abstract class OverlayWindowService : LifecycleService(), SavedStateRegistryOwner,
    OnSimpleLife {
    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

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

    val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    @Composable
    abstract fun ComposeContent()

    open fun onClickView() {}

    val view by lazy {
        ComposeView(this).apply {
            setViewTreeSavedStateRegistryOwner(this@OverlayWindowService)
            setViewTreeLifecycleOwner(this@OverlayWindowService)
            setContent { ComposeContent() }
        }
    }

    abstract val positionStoreKey: String

    private val minMargin: Int
        get() = 10.dp.px.toInt()

    val positionFlow by lazy {
        createTextFlow(
            key = positionStoreKey,
            decode = {
                (it ?: "").split(',', limit = 2).mapNotNull { v -> v.toIntOrNull() }.run {
                    if (size == 2) {
                        get(0) to get(1)
                    } else {
                        minMargin to BarUtils.getStatusBarHeight()
                    }
                }
            },
            encode = {
                "${it.first},${it.second}"
            },
            scope = lifecycleScope,
            debounceMillis = 300,
        )
    }

    init {
        val flag = System.currentTimeMillis()
        onCreated { instanceFlags.add(flag) }
        onDestroyed { instanceFlags.remove(flag) }
        onCreated {
            lifecycleScope.launch {
                var canDrawOverlays = canDrawOverlaysState.updateAndGet()
                topActivityFlow.mapState(lifecycleScope) { it.appId to it.activityId }
                    .filter { flag == instanceFlags.last() }
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
                x = positionFlow.value.first
                y = positionFlow.value.second
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
                    positionFlow.value = x to y
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
                            positionFlow.value = layoutParams.x to layoutParams.y
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