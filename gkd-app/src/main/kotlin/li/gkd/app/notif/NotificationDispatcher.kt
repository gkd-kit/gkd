package li.gkd.app.notif

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
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.LogUtils
import li.gkd.app.util.componentName

object NotificationDispatcher {
    private val pendingIntentFlags get() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun build(spec: AppNotificationSpec): Notification {
        val contentIntent = PendingIntent.getActivity(
            app,
            spec.id,
            Intent().apply {
                component = MainActivity::class.componentName
                // MainActivity uses singleTask, receives navigation via onNewIntent; cannot clear the existing task stack.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
                .addAction(0, UiStrings.action_stop, stopIntent)
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
            // When the system automatically restarts a Service with startRequested, it will not go through the app-side startup check,
            // And permissions may change after the check, so SecurityException fallback is still needed here.
            ServiceCompat.startForeground(
                service,
                notification.id,
                build(notification),
                if (AndroidTarget.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST else -1,
            )
            true
        } catch (e: SecurityException) {
            service.canStartForeground()
            LogUtils.d("Foreground service failed to start", service.javaClass.name, e)
            service.stopSelf()
            false
        }
    }
}
