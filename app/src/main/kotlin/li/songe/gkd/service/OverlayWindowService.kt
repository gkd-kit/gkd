package li.songe.gkd.service


import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import li.songe.gkd.store.createTextFlow
import li.songe.gkd.util.OnCreateToDestroy
import li.songe.gkd.util.px


abstract class OverlayWindowService : LifecycleService(), SavedStateRegistryOwner,
    OnCreateToDestroy {
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

    val positionFlow by lazy {
        createTextFlow(
            key = positionStoreKey,
            decode = {
                val list = (it ?: "").split(',', limit = 2).takeIf { l -> l.size == 2 }
                if (list != null) {
                    val a = list.getOrNull(0)?.toIntOrNull() ?: 0
                    val b = list.getOrNull(1)?.toIntOrNull() ?: 0
                    a to b
                } else {
                    0 to 0
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
        onCreated {
            val marginX = 20.dp.px.toInt()
            val marginY = BarUtils.getStatusBarHeight() + 5.dp.px.toInt()
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
                val sharedFlow = MutableSharedFlow<Unit>()
                view.viewTreeObserver.addOnGlobalLayoutListener {
                    launch { sharedFlow.emit(Unit) }
                }
                sharedFlow.debounce(100).collect { fixLimitXy() }
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