package li.gkd.app.util

import li.gkd.app.text.UiStrings
import li.gkd.app.util.ToastUtils.toast

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.lifecycle.MainActivityVisibility
import li.songe.codeorigin.CallSite
import kotlin.reflect.KClass

object IntentUtils {
    fun openWeChatScaner() {
        val intent = app.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.apply {
            putExtra("LauncherUI.From.Scaner.Shortcut", true)
        }
        if (intent == null) {
            toast(UiStrings.wechat_unavailable)
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
        val parsedUri = try {
            uri.toUri()
        } catch (e: Exception) {
            e.printStackTrace()
            toast(UiStrings.link_invalid)
            return
        }
        openUri(parsedUri)
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

    fun <T : Service> startForegroundServiceByClass(
        clazz: KClass<T>,
        @CallSite loc: String = "",
    ) {
        if (!PermissionStates.notification.checkOrToast(loc = loc)) return
        if (!PermissionStates.foregroundServiceSpecialUse.checkOrToast(loc = loc)) return
        val intent = Intent(app, clazz.java)
        try {
            app.startForegroundService(intent)
        } catch (e: Throwable) {
            LogUtils.d(e, loc = loc)
            val prefix = if (MainActivityVisibility.isVisible) "" else "${META.appName}: "
            toast(UiStrings.service_launch_failed(prefix, e.message), forced = true, loc = loc)
        }
    }
}
