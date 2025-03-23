package li.songe.gkd.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.core.graphics.get
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.activityManager
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

inline fun <T, R> Iterable<T>.mapHashCode(transform: (T) -> R): Int {
    return fold(0) { acc, t -> 31 * acc + transform(t).hashCode() }
}

private val componentNameCache by lazy { HashMap<String, ComponentName>() }

val KClass<*>.componentName
    get() = componentNameCache.getOrPut(jvmName) { ComponentName(META.appId, jvmName) }

fun Bitmap.isEmptyBitmap(): Boolean {
    // png
    repeat(width) { x ->
        repeat(height) { y ->
            if (this[x, y] != 0) {
                return false
            }
        }
    }
    return true
}

class InterruptRuleMatchException() : Exception()

fun getShowActivityId(appId: String, activityId: String?): String? {
    return if (activityId != null) {
        if (activityId.startsWith(appId) && activityId.getOrNull(appId.length) == '.') {
            activityId.substring(appId.length)
        } else {
            activityId
        }
    } else {
        null
    }
}

fun MainActivity.fixSomeProblems() {
    fixStatusPaddingTopWhenSystemShare()
    fixTransparentNavigationBar()
}

// 当调用系统分享时来到 android/com.android.internal.app.MiuiChooserActivity
// 在某些设备上此界面有时不显示状态栏, 导致应用整体上移
private fun MainActivity.fixStatusPaddingTopWhenSystemShare() {
    var tempTop = Resources.getSystem().run {
        @SuppressLint("DiscouragedApi", "InternalInsetResource")
        getDimensionPixelSize(getIdentifier("status_bar_height", "dimen", "android"))
    }
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
        view.updatePadding(top = 0)
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        if (insets.top == 0 && activityManager.appTasks.firstOrNull()?.taskInfo?.topActivity?.packageName != packageName) {
            view.setBackgroundColor(Color.TRANSPARENT)
            view.updatePadding(top = tempTop)
        }
        ViewCompat.onApplyWindowInsets(view, windowInsets)
    }
}

private fun Activity.fixTransparentNavigationBar() {
    // 修复在浅色主题下导航栏背景不透明的问题
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    } else {
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
    }
}


fun <S : Comparable<S>> AnimatedContentTransitionScope<S>.getUpDownTransform(): ContentTransform {
    return if (targetState > initialState) {
        slideInVertically { height -> height } + fadeIn() togetherWith
                slideOutVertically { height -> -height } + fadeOut()
    } else {
        slideInVertically { height -> -height } + fadeIn() togetherWith
                slideOutVertically { height -> height } + fadeOut()
    }.using(
        SizeTransform(clip = false)
    )
}
