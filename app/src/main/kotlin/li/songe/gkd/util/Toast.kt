package li.songe.gkd.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Toast
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.hjq.toast.Toaster
import com.hjq.toast.style.WhiteToastStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.activityVisibleFlow
import li.songe.gkd.app
import li.songe.gkd.appScope


private var lastToast: Toast? = null

fun toast(text: CharSequence) {
    appScope.launch(Dispatchers.Main) {
        try {
            lastToast?.cancel()
            lastToast = null
        } catch (e: Exception) {
            LogUtils.d("lastToast?.cancel", e)
        }
        try {
            Toaster.cancel()
        } catch (e: Exception) {
            LogUtils.d("Toaster.cancel", e)
        }
        try {
            if (activityVisibleFlow.value <= 0) {
                lastToast = Toast.makeText(app, text, Toast.LENGTH_SHORT).apply {
                    show()
                }
            } else {
                Toaster.show(text)
            }
        } catch (e: Exception) {
            LogUtils.d("toast失败", e)
        }
    }
}

fun updateToastStyle(darkTheme: Boolean) {
    Toaster.setStyle(object : WhiteToastStyle() {
        override fun getGravity() = Gravity.BOTTOM
        override fun getYOffset() = (ScreenUtils.getScreenHeight() * 0.12f).toInt()
        override fun getTranslationZ(context: Context?) = ConvertUtils.dp2px(1f).toFloat()
        override fun getTextColor(context: Context?): Int {
            return if (darkTheme) Color.WHITE else Color.BLACK
        }

        override fun getBackgroundDrawable(context: Context?): Drawable {
            return (super.getBackgroundDrawable(context) as GradientDrawable).apply {
                setColor(if (!darkTheme) Color.WHITE else Color.parseColor("#191a1c"))
            }
        }
    })
}