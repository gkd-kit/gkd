package li.gkd.app.util

import android.content.ClipData
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
import li.gkd.app.text.UiStrings
import li.gkd.app.app
import li.gkd.app.data.ResolvedRule
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.lifecycle.MainActivityVisibility
import li.gkd.app.service.A11yService
import li.gkd.app.service.OverlayWindowService
import li.gkd.app.store.AppStore.actionCountFlow
import li.gkd.app.store.AppStore.storeFlow
import li.songe.codeorigin.CallSite

object ToastUtils {
    fun toast(
        text: CharSequence,
        forced: Boolean = false,
        delayMillis: Long = 0L,
        @CallSite loc: String = "",
    ) {
        if (delayMillis > 0) {
            runMainPost(delayMillis) {
                toast(text = text, forced = forced, loc = loc)
            }
            return
        }
        if (forced || MainActivityVisibility.isVisible || OverlayWindowService.isAnyAlive) {
            Toaster.show(text)
        }
        if (loc.isNotEmpty()) {
            LogUtils.d(text, loc = loc)
        }
    }

    private val darkTheme: Boolean
        get() = storeFlow.value.enableDarkTheme ?: app.resources.configuration.let {
            it.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }

    private val toastYOffset: Int
        get() = (ScreenUtils.getScreenHeight() * 0.12f).toInt()

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
                        (12.dp.px * 2 + 20.sp.px) / 2f
                    )
                }
            }
        }
    }

    private fun View.updateToastView() {
        setPaddingRelative(
            16.dp.px.toInt(),
            12.dp.px.toInt(),
            16.dp.px.toInt(),
            12.dp.px.toInt(),
        )
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (this is TextView) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 14.sp.px)
            setTextColor(if (darkTheme) Color.WHITE else Color.BLACK)
            if (AndroidTarget.Q) {
                breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
            }
        }
        background = GradientDrawable().apply {
            setColor((if (darkTheme) "#303030" else "#fafafa").toColorInt())
        }
        outlineProvider = circleOutlineProvider
        clipToOutline = true
        elevation = 2.dp.px
    }

    private fun setReactiveToastStyle() {
        Toaster.setStyle(object : WhiteToastStyle() {
            override fun getGravity() = Gravity.BOTTOM
            override fun getYOffset() = toastYOffset
            override fun getTranslationZ(context: Context?) = 0f
            override fun createView(context: Context?): View {
                return super.createView(context).apply {
                    updateToastView()
                }
            }
        })
    }

    private var triggerTime = 0L
    private const val triggerInterval = 2000L
    fun showActionToast(rule: ResolvedRule) {
        if (!storeFlow.value.toastWhenClick) return
        runMainPost {
            val t = System.currentTimeMillis()
            if (t - triggerTime > triggerInterval + 100) { // 100ms to ensure the previous one is completely gone when showing for the second time
                triggerTime = t
                val text = ActionToastTemplate.render(
                    storeFlow.value.actionToast,
                    rule.rule.name.toString(),
                    rule.g.group.name,
                    actionCountFlow.value,
                )
                if (storeFlow.value.useSystemToast) {
                    showSystemToast(text)
                } else {
                    showA11yToast(text)
                }
            }
        }
    }

    fun previewActionToast(text: CharSequence, useSystemToast: Boolean) {
        runMainPost {
            if (useSystemToast) showSystemToast(text) else showA11yToast(text)
        }
    }

    private var cancelCustomToast: (() -> Unit)? = null
    private var cacheToast: Toast? = null
    private fun showSystemToast(message: CharSequence) {
        cancelCustomToast?.invoke()
        cacheToast?.cancel()
        cacheToast = Toast.makeText(app, message, Toast.LENGTH_SHORT).apply {
            show()
        }
    }

    // 1.Using WeakReference<View> causes inability to cancel on certain models
    // 2.Using coroutine delay + cacheView may also fail to cancel
    // https://github.com/gkd-kit/gkd/issues/697
    // https://github.com/gkd-kit/gkd/issues/698
    private fun showA11yToast(message: CharSequence) {
        cancelCustomToast?.invoke()
        cacheToast?.cancel()
        cacheToast = null
        val wm = A11yService.instance?.wm
            ?: if (PermissionStates.drawOverlays.updateAndGet()) app.windowManager else null
        if (wm == null) {
            showSystemToast(message)
            return
        }
        val textView = TextView(app).apply {
            text = message
            id = android.R.id.message
            gravity = Gravity.CENTER
            updateToastView()
        }
        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (wm == app.windowManager) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
            } catch (_: Exception) {
            }
        }
        cancelCustomToast = cancel
        runMainPost(triggerInterval) {
            if (cancelCustomToast === cancel) {
                cancel()
            }
        }
    }

    fun copyText(text: String) {
        app.clipboardManager.setPrimaryClip(ClipData.newPlainText(app.packageName, text))
        toast(UiStrings.copy_success)
    }

    fun initToast() {
        Toaster.init(app)
        Toaster.setDebugMode(false)
        Toaster.setInterceptor { false } // Override default interceptor
        setReactiveToastStyle()
    }
}
