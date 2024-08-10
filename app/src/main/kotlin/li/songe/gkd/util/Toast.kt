package li.songe.gkd.util

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import com.blankj.utilcode.util.ScreenUtils
import com.hjq.toast.Toaster
import com.hjq.toast.style.WhiteToastStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.service.GkdAbService


fun toast(text: CharSequence) {
    Toaster.show(text)
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
    }
    background = GradientDrawable().apply {
        setColor(Color.parseColor(if (darkTheme) "#303030" else "#fafafa"))
    }
    outlineProvider = circleOutlineProvider
    clipToOutline = true
    elevation = 2.dp.px
    outlineProvider = circleOutlineProvider
    clipToOutline = true
}

fun setReactiveToastStyle() {
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

private var actionTriggerTime = 0L
private const val actionTriggerInterval = 2000L
fun showActionToast(context: AccessibilityService) {
    if (!storeFlow.value.toastWhenClick) return
    val t = System.currentTimeMillis()
    if (t - actionTriggerTime <= actionTriggerInterval) return
    actionTriggerTime = t
    appScope.launchTry(Dispatchers.Main) {
        if (storeFlow.value.useSystemToast) {
            showSystemToast(storeFlow.value.clickToast)
        } else {
            showAccessibilityToast(
                context,
                storeFlow.value.clickToast
            )
        }
    }
}

private var cacheToast: Toast? = null
private fun showSystemToast(message: CharSequence) {
    cacheToast?.cancel()
    cacheToast = Toast.makeText(app, message, Toast.LENGTH_SHORT).apply {
        show()
    }
}

// 使用 WeakReference<View> 在某些机型上导致无法取消
// https://github.com/gkd-kit/gkd/issues/697
// https://github.com/gkd-kit/gkd/issues/698
@SuppressLint("StaticFieldLeak")
private var cacheToastView: View? = null
private suspend fun showAccessibilityToast(context: AccessibilityService, message: CharSequence) {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val textView = TextView(context).apply {
        text = message
        id = android.R.id.message
        gravity = Gravity.CENTER
        updateToastView()
    }

    val layoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = arrayOf(
            flags,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        ).reduce { acc, i -> acc or i }
        packageName = context.packageName
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.BOTTOM
        y = toastYOffset
        windowAnimations = android.R.style.Animation_Toast
    }

    cacheToastView?.let {
        if (it.context === context) {
            try {
                wm.removeViewImmediate(it)
            } catch (_: Exception) {
            }
        }
        cacheToastView = null
    }
    wm.addView(textView, layoutParams)
    cacheToastView = textView
    delay(actionTriggerInterval)
    if (GkdAbService.service != null && cacheToastView === textView) {
        try {
            wm.removeViewImmediate(textView)
        } catch (_: Exception) {
        }
    }
    cacheToastView = null
}
