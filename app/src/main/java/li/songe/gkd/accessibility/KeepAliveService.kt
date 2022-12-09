package li.songe.gkd.accessibility

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.App
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.service.CompositionService
import kotlin.concurrent.thread

class KeepAliveService : CompositionService() {
    companion object {
        fun start(context: Context = App.context) {
            context.startForegroundService(Intent(context, KeepAliveService::class.java))
        }
    }
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        LogUtils.d("start")

        var isRunning = true
        onDestroy {
            isRunning = false
        }
        thread {
            while (isRunning) {
                Thread.sleep(3_000)
            }
        }
    }

    private fun createNotificationChannel() {
        val channelId = "CHANNEL_TEST"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_app_2)
            .setContentTitle("调试模式2")
            .setContentText("测试后台任务")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setAutoCancel(false)

        val name = "调试模式2"
        val descriptionText = "测试后台任务"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(channel)
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                110,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        } else {
            startForeground(110, notification)
        }
    }
}