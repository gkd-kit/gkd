package li.songe.gkd.util

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.sp
import androidx.core.graphics.get
import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import li.songe.json5.Json5EncoderConfig
import li.songe.json5.encodeToJson5String
import java.io.DataOutputStream
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

private val componentNameCache by lazy { HashMap<String, ComponentName>() }

val KClass<*>.componentName
    get() = componentNameCache.getOrPut(jvmName) { ComponentName(META.appId, jvmName) }

fun Bitmap.isFullTransparent(): Boolean {
    repeat(width) { x ->
        repeat(height) { y ->
            if (this[x, y] != Color.TRANSPARENT) {
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
    fixTransparentNavigationBar()
}

private fun Activity.fixTransparentNavigationBar() {
    // 修复在浅色主题下导航栏背景不透明的问题
    if (AndroidTarget.Q) {
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

suspend fun runCommandByRoot(commandText: String) {
    var p: Process? = null
    try {
        p = Runtime.getRuntime().exec("su")
        val o = DataOutputStream(p.outputStream)
        o.writeBytes("${commandText}\nexit\n")
        o.flush()
        o.close()
        p.waitFor()
        if (p.exitValue() == 0) {
            return
        }
    } catch (e: Exception) {
        toast("运行失败:${e.message}")
        LogUtils.d(e)
    } finally {
        p?.destroy()
    }
    stopCoroutine()
}

val defaultJson5Config = Json5EncoderConfig(indent = "\u0020\u0020", trailingComma = true)
inline fun <reified T> toJson5String(value: T): String {
    return json.encodeToJson5String(value, defaultJson5Config)
}

fun drawTextToBitmap(text: String, bitmap: Bitmap) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32.sp.px
        color = Color.BLUE
        textAlign = Paint.Align.CENTER
    }
    val canvas = Canvas(bitmap)
    val strList = text.split('\n')
    strList.forEachIndexed { i, str ->
        canvas.drawText(
            str,
            bitmap.width / 2f,
            (bitmap.height / 2f) + (i - strList.size / 2f) * (paint.textSize + 4.sp.px),
            paint
        )
    }
}

// https://github.com/gkd-kit/gkd/issues/44
// java.lang.ClassNotFoundException:Didn't find class "android.app.IActivityTaskManager" on path: DexPathList
private val clazzMap = HashMap<String, Boolean>()
fun checkExistClass(className: String): Boolean = clazzMap[className] ?: try {
    Class.forName(className)
    true
} catch (_: Throwable) {
    false
}.apply {
    clazzMap[className] = this
}

// https://github.com/gkd-kit/gkd/issues/924
private val Drawable.safeDrawable: Drawable?
    get() = if (intrinsicHeight > 0 && intrinsicWidth > 0) {
        this
    } else {
        null
    }

val PackageInfo.pkgIcon: Drawable?
    get() = applicationInfo?.loadIcon(app.packageManager)?.safeDrawable

private fun Char.isAsciiLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}

private fun Char.isAsciiVar(): Boolean {
    return this.isAsciiLetter() || this in '0'..'9' || this == '_'
}

private fun Char.isAsciiClassVar(): Boolean {
    return this.isAsciiVar() || this == '$'
}

// https://developer.android.com/build/configure-app-module?hl=zh-cn
fun String.isValidAppId(): Boolean {
    if (!contains('.')) return false
    if (!first().isAsciiLetter()) return false
    var i = 0
    while (i < length) {
        val c = get(i)
        if (c == '.') {
            i++
            if (getOrNull(i)?.isAsciiLetter() != true) {
                return false
            }
        } else if (!c.isAsciiVar()) {
            return false
        }
        i++
    }
    return true
}

fun String.isValidActivityId(): Boolean {
    if (isEmpty()) return false
    var i = 0
    while (i < length) {
        val c = get(i)
        if (c == '.') {
            i++
            if (getOrNull(i)?.isAsciiClassVar() == false) {
                return false
            }
        } else if (!c.isAsciiClassVar()) {
            return false
        }
        i++
    }
    return true
}

object AppListString {
    fun decode(text: String): Set<String> {
        return text.split('\n').filter { a -> a.isValidAppId() }.toHashSet()
    }

    fun encode(set: Set<String>, append: Boolean = false): String {
        val list = set.sorted()
        if (append) {
            return list.joinToString(separator = "\n\n", postfix = "\n\n") {
                val name = appInfoMapFlow.value[it]?.name
                if (name != null) {
                    "$it\n# $name"
                } else {
                    it
                }
            }
        }
        return list.joinToString("\n")
    }

    fun getDefaultBlockList(): Set<String> {
        val set = hashSetOf(META.appId, systemUiAppId)
        listOf(
            Intent.ACTION_MAIN to Intent.CATEGORY_HOME,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_GALLERY,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_CONTACTS,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_CALENDAR,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_MESSAGING,
            Intent.ACTION_MAIN to Intent.CATEGORY_APP_CALCULATOR,
            Intent.ACTION_OPEN_DOCUMENT to Intent.CATEGORY_OPENABLE,
            AlarmClock.ACTION_SHOW_ALARMS to null,
            MediaStore.ACTION_IMAGE_CAPTURE to null,
            Settings.ACTION_SETTINGS to null,
        ).forEach {
            app.resolveAppId(it.first, it.second)?.let(set::add)
        }
        return set
    }
}
