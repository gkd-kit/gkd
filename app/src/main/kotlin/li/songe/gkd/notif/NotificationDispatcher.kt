package li.songe.gkd.notif

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.componentName

object NotificationDispatcher {
    private val pendingIntentFlags get() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun build(spec: AppNotificationSpec): Notification {
        val contentIntent = PendingIntent.getActivity(
            app,
            spec.id,
            Intent().apply {
                component = MainActivity::class.componentName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                data = spec.uri?.toUri()
            },
            pendingIntentFlags,
        )
        val builder = NotificationCompat.Builder(app, spec.channel.id)
            .setSmallIcon(spec.smallIcon)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setContentIntent(contentIntent)
            .setOngoing(spec.ongoing)
            .setAutoCancel(spec.autoCancel)

        spec.stopService?.let { serviceClass ->
            val stopIntent = PendingIntent.getBroadcast(
                app,
                spec.id,
                StopServiceReceiver.getIntent(serviceClass),
                pendingIntentFlags,
            )
            builder
                .setDeleteIntent(stopIntent)
                .addAction(0, "停止", stopIntent)
        }
        return builder.build()
    }

    fun post(notification: PostedNotification) {
        if (!PermissionStates.notification.updateAndGet()) return
        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(app).notify(notification.id, build(notification))
    }

    private fun Service.canStartForeground(): Boolean {
        if (!AndroidTarget.UPSIDE_DOWN_CAKE) return true
        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(this, javaClass),
            PackageManager.ComponentInfoFlags.of(0),
        )
        val usesSpecialUse =
            serviceInfo.foregroundServiceType and
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
        return !usesSpecialUse ||
                PermissionStates.foregroundServiceSpecialUse.updateAndGet()
    }

    fun startForeground(service: Service, notification: ForegroundNotification): Boolean {
        if (!service.canStartForeground()) {
            service.stopSelf()
            return false
        }
        return try {
            // 系统自动重启 startRequested 的 Service 时不会经过应用侧的启动检查，
            // 且权限可能在检查后发生变化，因此这里仍需兜底 SecurityException。
            ServiceCompat.startForeground(
                service,
                notification.id,
                build(notification),
                if (AndroidTarget.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST else -1,
            )
            true
        } catch (e: SecurityException) {
            service.canStartForeground()
            LogUtils.d("前台服务启动失败", service.javaClass.name, e)
            service.stopSelf()
            false
        }
    }
}
