package li.gkd.app.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.text.LineBreaker
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.hjq.toast.Toaster
import com.hjq.toast.style.WhiteToastStyle
import li.gkd.app.app
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.permission.PermissionStates
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.service.A11yService
import li.gkd.app.settings.SettingsRepository.actionCount
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.util.ActionToastTemplate

object AndroidToastUtils {
    private val darkTheme: Boolean
        get() =
            settings.value.enableDarkTheme
                ?: app.resources.configuration.let {
                    it.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
                }

    private val toastYOffset: Int
        get() = (ScreenUtils.getSize().height * 0.12f).toInt()

    private val circleOutlineProvider by lazy {
        object : ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                if (view != null && outline != null) {
                    // 20.sp : line height, 12.dp : top/bottom padding
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        (12.dp.px * 2 + 20.sp.px) / 2f,
                    )
                }
            }
        }
    }

    private fun View.updateStyle() {
        setPaddingRelative(
            16.dp.px.toInt(),
            12.dp.px.toInt(),
            16.dp.px.toInt(),
            12.dp.px.toInt(),
        )
        layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        if (this is TextView) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 14.sp.px)
            setTextColor(if (darkTheme) Color.WHITE else Color.BLACK)
            if (AndroidTarget.Q) {
                breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
            }
        }
        background =
            GradientDrawable().apply {
                setColor((if (darkTheme) "#303030" else "#fafafa").toColorInt())
            }
        outlineProvider = circleOutlineProvider
        clipToOutline = true
        elevation = 2.dp.px
    }

    private fun setReactiveStyle() {
        Toaster.setStyle(
            object : WhiteToastStyle() {
                override fun getGravity() = Gravity.BOTTOM

                override fun getYOffset() = toastYOffset

                override fun getTranslationZ(context: Context) = 0f

                override fun createView(context: Context): View {
                    return super.createView(context).apply {
                        updateStyle()
                    }
                }
            },
        )
    }

    private var triggerTime = 0L
    private const val triggerInterval = 2000L

    fun showAction(rule: ResolvedRule, runId: Long = 0) {
        if (!settings.value.toastWhenClick) return
        val trace = MatchingRuntime.coordinator.diagnostics
        val dispatch =
            if (trace.enabled) trace.beginOperation("ToastDispatch", runId, rule.matchIdentity())
            else null
        ThreadUtils.runMainOrPost {
            trace.endOperation(dispatch, "main_dispatched")
            val t = System.currentTimeMillis()
            if (t - triggerTime > triggerInterval + 100) { // 100ms 保证二次显示的时候上一次已经完全消失
                triggerTime = t
                val text =
                    ActionToastTemplate.render(
                        settings.value.actionToast,
                        rule.rule.name.toString(),
                        rule.g.group.name,
                        actionCount.value,
                    )
                if (dispatch != null && trace.enabled)
                    trace.record(
                        runId,
                        MatchStage.Idle,
                        "toast_show_requested",
                        dispatch.rule,
                        entrySource = "toast",
                        sessionId = dispatch.sessionId,
                    )
                if (settings.value.useSystemToast) {
                    showSystem(text)
                } else {
                    showA11y(text)
                }
            } else if (dispatch != null && trace.enabled)
                trace.record(
                    runId,
                    MatchStage.Idle,
                    "toast_throttled",
                    dispatch.rule,
                    entrySource = "toast",
                    sessionId = dispatch.sessionId,
                )
        }
    }

    fun previewAction(text: CharSequence, useSystemToast: Boolean) {
        ThreadUtils.runMainOrPost {
            if (useSystemToast) showSystem(text) else showA11y(text)
        }
    }

    private var cancelCustomToast: (() -> Unit)? = null
    private var cacheToast: Toast? = null

    private fun showSystem(message: CharSequence) {
        cancelCustomToast?.invoke()
        cacheToast?.cancel()
        cacheToast =
            Toast.makeText(app, message, Toast.LENGTH_SHORT).apply {
                show()
            }
    }

    // 1.使用 WeakReference<View> 在某些机型上导致无法取消
    // 2.使用协程 delay + cacheView 也可能导致无法取消
    // https://github.com/gkd-kit/gkd/issues/697
    // https://github.com/gkd-kit/gkd/issues/698
    private fun showA11y(message: CharSequence) {
        cancelCustomToast?.invoke()
        cacheToast?.cancel()
        cacheToast = null
        val wm =
            A11yService.instance?.wm
                ?: if (PermissionStates.drawOverlays.updateAndGet()) app.windowManager else null
        if (wm == null) {
            showSystem(message)
            return
        }
        val textView =
            TextView(app).apply {
                text = message
                id = android.R.id.message
                gravity = Gravity.CENTER
                updateStyle()
            }
        val layoutParams =
            WindowManager.LayoutParams().apply {
                type =
                    if (wm == app.windowManager) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    }
                if (type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                    alpha = 0.8f
                }
                format = PixelFormat.TRANSLUCENT
                flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                packageName = app.packageName
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
                y = toastYOffset
                windowAnimations = android.R.style.Animation_Toast
            }
        wm.addView(textView, layoutParams)
        val cancel: () -> Unit = {
            cancelCustomToast = null
            try {
                wm.removeViewImmediate(textView)
            } catch (_: Exception) {}
        }
        cancelCustomToast = cancel
        ThreadUtils.runMainOrPost(triggerInterval) {
            if (cancelCustomToast === cancel) {
                cancel()
            }
        }
    }

    fun init() {
        Toaster.init(app)
        Toaster.setDebugMode(false)
        Toaster.setInterceptor { false } // 覆盖默认拦截器
        setReactiveStyle()
    }
}
