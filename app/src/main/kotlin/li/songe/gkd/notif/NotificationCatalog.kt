package li.songe.gkd.notif

import android.app.Service
import li.songe.gkd.META
import li.songe.gkd.R
import li.songe.gkd.service.ActivityService
import li.songe.gkd.service.ButtonService
import li.songe.gkd.service.EventService
import li.songe.gkd.service.HttpService
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.service.TrackService
import kotlin.reflect.KClass

enum class ForegroundNotificationKey(
    val id: Int,
    val channel: AppNotificationChannel = AppNotificationChannel.Service,
) {
    Status(id = 100),
    Screenshot(id = 101),
    Button(id = 102),
    Http(id = 103),
    Expose(id = 104),
    Activity(id = 106),
    Event(id = 107),
    Track(id = 108),
}

enum class PostedNotificationKey(
    val id: Int,
    val channel: AppNotificationChannel,
) {
    SnapshotSaved(id = 105, channel = AppNotificationChannel.Snapshot),
}

sealed interface AppNotificationSpec {
    val id: Int
    val channel: AppNotificationChannel
    val smallIcon: Int
    val title: String
    val text: String?
    val uri: String?
    val ongoing: Boolean
    val autoCancel: Boolean
    val stopService: KClass<out Service>?
}

data class ForegroundNotification(
    val key: ForegroundNotificationKey,
    override val title: String,
    override val text: String? = null,
    override val uri: String? = null,
    override val smallIcon: Int = R.drawable.ic_status,
    override val stopService: KClass<out Service>? = null,
) : AppNotificationSpec {
    override val id: Int
        get() = key.id
    override val channel: AppNotificationChannel
        get() = key.channel
    override val ongoing = true
    override val autoCancel = false

    context(service: Service)
    fun startForeground() {
        NotificationDispatcher.startForeground(service, this)
    }
}

data class PostedNotification(
    val key: PostedNotificationKey,
    override val title: String,
    override val text: String? = null,
    override val uri: String? = null,
    override val smallIcon: Int = R.drawable.ic_status,
    override val ongoing: Boolean = false,
    override val autoCancel: Boolean = true,
) : AppNotificationSpec {
    override val id: Int
        get() = key.id
    override val channel: AppNotificationChannel
        get() = key.channel
    override val stopService: KClass<out Service>? = null

    fun post() {
        NotificationDispatcher.post(this)
    }
}

object NotificationCatalog {
    fun status(
        title: String = META.appName,
        text: String? = "无障碍正在运行",
        uri: String? = null,
    ) = ForegroundNotification(
        key = ForegroundNotificationKey.Status,
        title = title,
        text = text,
        uri = uri,
    )

    fun screenshot() = ForegroundNotification(
        key = ForegroundNotificationKey.Screenshot,
        title = "截屏服务正在运行",
        text = "保存快照时截取屏幕",
        uri = "gkd://page/1",
        stopService = ScreenshotService::class,
    )

    fun button() = ForegroundNotification(
        key = ForegroundNotificationKey.Button,
        title = "快照按钮服务正在运行",
        text = "点击按钮捕获快照",
        uri = "gkd://page/1",
        stopService = ButtonService::class,
    )

    fun http() = ForegroundNotification(
        key = ForegroundNotificationKey.Http,
        title = "HTTP服务正在运行",
        uri = "gkd://page/1",
        stopService = HttpService::class,
    )

    fun expose() = ForegroundNotification(
        key = ForegroundNotificationKey.Expose,
        title = "运行外部调用任务中",
        text = "任务完成后自动关闭",
    )

    fun snapshotSaved(text: String) = PostedNotification(
        key = PostedNotificationKey.SnapshotSaved,
        title = "快照已保存",
        text = text,
        uri = "gkd://page/2",
    )

    fun activity(text: String? = null) = ForegroundNotification(
        key = ForegroundNotificationKey.Activity,
        title = "记录服务正在运行",
        text = text,
        uri = "gkd://page/1",
        stopService = ActivityService::class,
    )

    fun event() = ForegroundNotification(
        key = ForegroundNotificationKey.Event,
        title = "事件服务正在运行",
        uri = "gkd://page/1",
        stopService = EventService::class,
    )

    fun track() = ForegroundNotification(
        key = ForegroundNotificationKey.Track,
        title = "轨迹服务正在运行",
        uri = "gkd://page?tab=3",
        stopService = TrackService::class,
    )
}
