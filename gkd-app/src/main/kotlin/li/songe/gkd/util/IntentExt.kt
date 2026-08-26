package li.songe.gkd.util

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.PermissionStates
import kotlin.reflect.KClass

fun Context.tryStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d("tryStartActivity", e)
        toast("跳转失败\n" + (e.message ?: e.stackTraceToString()))
    }
}

fun openWeChatScaner() {
    val intent = app.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.apply {
        putExtra("LauncherUI.From.Scaner.Shortcut", true)
    }
    if (intent == null) {
        toast("请检查微信是否安装或禁用")
        return
    }
    app.tryStartActivity(intent)
}

fun openA11ySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    app.tryStartActivity(intent)
}

fun openAppDetailsSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${app.packageName}".toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    app.tryStartActivity(intent)
}

fun openUri(uri: String) {
    val u = try {
        uri.toUri()
    } catch (e: Exception) {
        e.printStackTrace()
        toast("非法链接")
        return
    }
    openUri(u)
}

fun openUri(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    app.tryStartActivity(intent)
}

fun <T : Service> stopServiceByClass(clazz: KClass<T>) {
    val intent = Intent(app, clazz.java)
    app.stopService(intent)
}

fun <T : Service> startForegroundServiceByClass(clazz: KClass<T>) {
    if (!PermissionStates.notification.checkOrToast()) return
    if (!PermissionStates.foregroundServiceSpecialUse.checkOrToast()) return
    val intent = Intent(app, clazz.java)
    try {
        app.startForegroundService(intent)
    } catch (e: Throwable) {
        LogUtils.d(e)
        val prefix = if (isActivityVisible) "" else "${META.appName}: "
        toast("${prefix}启动服务失败: ${e.message}", forced = true)
    }
}

val Intent.extraCptName: ComponentName?
    get() = if (AndroidTarget.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_COMPONENT_NAME) as? ComponentName?
    }
