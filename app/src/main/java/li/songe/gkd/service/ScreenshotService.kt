package li.songe.gkd.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ServiceUtils
import li.songe.gkd.App
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.util.ScreenshotUtil

// 录屏功能必须提供通知, 此通知只能通过关闭服务来销毁, 所以录屏功能不能放在无障碍服务里
class ScreenshotService : Service() {
    companion object {
        fun isRunning() = ServiceUtils.isServiceRunning(ScreenshotService::class.java)
        suspend fun screenshot() = service?.screenshotUtil?.execute()
        private const val devServerNotificationId: Int = 110
        private var service: ScreenshotService? = null
        fun stop(context: Context = App.context) {
            if (isRunning()) {
                context.stopService(Intent(context, ScreenshotService::class.java))
            }
        }

        fun start(context: Context = App.context, intent: Intent) {
            intent.putExtra("screenshot", true)
            intent.component = ComponentName(context, ScreenshotService::class.java)
            context.startForegroundService(intent)
        }
    }

    private var screenshotUtil: ScreenshotUtil? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        service = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(
                "screenshot",
                false
            ) == true
        ) {
            screenshotUtil?.destroy()
            screenshotUtil = ScreenshotUtil(this, intent)
            LogUtils.d("screenshot start")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotUtil?.destroy()
        screenshotUtil = null
        service = null
        LogUtils.d("screenshot end")
    }

    private fun createNotificationChannel() {
        val channelId = "CHANNEL_ID"
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
            .setContentTitle("调试模式")
            .setContentText("正在录制您的屏幕内容")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setAutoCancel(false)

        val name = "调试模式"
        val descriptionText = "屏幕录制"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(channel)
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                devServerNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(devServerNotificationId, notification)
        }
    }

}